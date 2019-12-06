// Copyright (c) Microsoft Corporation.

package com.microsoft.commondatamodel.objectmodel.cdm;

import com.microsoft.commondatamodel.objectmodel.enums.CdmObjectType;
import com.microsoft.commondatamodel.objectmodel.storage.StorageAdapter;
import com.microsoft.commondatamodel.objectmodel.utilities.CopyOptions;
import com.microsoft.commondatamodel.objectmodel.utilities.ResolveOptions;
import com.microsoft.commondatamodel.objectmodel.utilities.StringUtils;
import com.microsoft.commondatamodel.objectmodel.utilities.VisitCallback;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CdmDataPartitionPatternDefinition extends CdmObjectDefinitionBase implements CdmFileStatus {

  private String name;
  private String rootLocation;
  private String regularExpression;
  private List<String> parameters;
  private String specializedSchema;
  private OffsetDateTime lastFileStatusCheckTime;
  private OffsetDateTime lastFileModifiedTime;
  private OffsetDateTime lastChildFileModifiedTime;

  public CdmDataPartitionPatternDefinition(final CdmCorpusContext ctx, final String name) {
    super(ctx);
    this.setObjectType(CdmObjectType.DataPartitionPatternDef);
    this.setName(name);
  }

  @Override
  public boolean validate() {
    return getRegularExpression() != null || !StringUtils.isNullOrTrimEmpty(getRootLocation());
  }

  /**
   *
   * @param resOpt
   * @param options
   * @return
   * @deprecated CopyData is deprecated. Please use the Persistence Layer instead. This function is
   * extremely likely to be removed in the public interface, and not meant to be called externally
   * at all. Please refrain from using it.
   */
  @Override
  @Deprecated
  public Object copyData(final ResolveOptions resOpt, final CopyOptions options) {
    return CdmObjectBase.copyData(this, resOpt, options, CdmDataPartitionPatternDefinition.class);
  }

  @Override
  public CdmObject copy(final ResolveOptions resOpt) {
    final CdmDataPartitionPatternDefinition copy = new CdmDataPartitionPatternDefinition(this.getCtx(), this.getName());

    copy.setRootLocation(this.getRootLocation());
    copy.setRegularExpression(this.getRegularExpression());
    copy.setParameters(this.getParameters());
    copy.setLastFileStatusCheckTime(this.getLastFileStatusCheckTime());
    copy.setLastFileModifiedTime(this.getLastFileModifiedTime());

    if (this.getSpecializedSchema() != null) {
      copy.setSpecializedSchema(this.getSpecializedSchema());
    }

    this.copyDef(resOpt, copy);

    return copy;
  }

  @Override
  public boolean isDerivedFrom(final ResolveOptions resOpt, final String baseDef) {
    return false;
  }

  @Override
  public boolean visit(final String pathRoot, final VisitCallback preChildren, final VisitCallback postChildren) {
    return false;
  }

  /**
   * Gets or sets the data partition pattern name.
   */
  @Override
  public String getName() {
    return name;
  }

  public void setName(final String value) {
    this.name = value;
  }

  /**
   * Gets or sets the starting location corpus path for searching for inferred data partitions.
   */
  public String getRootLocation() {
    return rootLocation;
  }

  public void setRootLocation(final String value) {
    this.rootLocation = value;
  }

  /**
   * Gets or sets the regular expression string to use for searching partitions.
   */
  public String getRegularExpression() {
    return regularExpression;
  }

  public void setRegularExpression(final String value) {
    this.regularExpression = value;
  }

  /**
   * Gets or sets the names for replacement values from regular expression.
   */
  public List<String> getParameters() {
    return parameters;
  }

  public void setParameters(final List<String> value) {
    this.parameters = value;
  }

  /**
   * Gets or sets the corpus path for specialized schema to use for matched pattern partitions.
   */
  public String getSpecializedSchema() {
    return specializedSchema;
  }

  public void setSpecializedSchema(final String value) {
    this.specializedSchema = value;
  }

  /**
   * Last time the modified times were updated.
   */
  @Override
  public OffsetDateTime getLastFileStatusCheckTime() {
    return lastFileStatusCheckTime;
  }

  @Override
  public void setLastFileStatusCheckTime(final OffsetDateTime value) {
    this.lastFileStatusCheckTime = value;
  }

  /**
   * Last time this file was modified according to the OM.
   */
  @Override
  public OffsetDateTime getLastFileModifiedTime() {
    return lastFileModifiedTime;
  }

  @Override
  public void setLastFileModifiedTime(final OffsetDateTime value) {
    this.lastFileModifiedTime = value;
  }

  @Override
  public OffsetDateTime getLastChildFileModifiedTime() {
    return lastChildFileModifiedTime;
  }

  @Override
  public void setLastChildFileModifiedTime(final OffsetDateTime time) {
    this.lastChildFileModifiedTime = time;
  }

  /**
   * Updates the object and any children with changes made in the document file where it came from.
   */
  @Override
  public CompletableFuture<Void> fileStatusCheckAsync() {
    return CompletableFuture.runAsync(() -> {
      final String nameSpace = getInDocument().getNamespace();
      final StorageAdapter adapter = getCtx().getCorpus().getStorage().fetchAdapter(nameSpace);

      // make sure the root is a good full corpus path
      String rootCleaned = getRootLocation();

      if (rootCleaned == null) {
        rootCleaned = "";
      }

      if (rootCleaned.endsWith("/")) {
        rootCleaned = StringUtils.slice(rootCleaned, rootCleaned.length() - 1);
      }

      final String rootCorpus = getCtx().getCorpus().getStorage().createAbsoluteCorpusPath(rootCleaned, getInDocument());

      // get a list of all corpusPaths under the root
      final List<String> fileInfoList = adapter.fetchAllFilesAsync(rootCorpus).join();

      // remove root of the search from the beginning of all paths so anything in the root is not found by regex
      for (int i = 0; i < fileInfoList.size(); i++) {
        fileInfoList.set(i, nameSpace + ":" + fileInfoList.get(i));
        fileInfoList.set(i, StringUtils.slice(fileInfoList.get(i), rootCorpus.length()));
      }

      final Pattern regexPattern = Pattern.compile(getRegularExpression());

      if (getOwner() instanceof CdmLocalEntityDeclarationDefinition) {
        for (final String fi : fileInfoList) {
          final Matcher m = regexPattern.matcher(fi);

          if (m.matches() && m.group().equals(fi)) {
            // create a map of arguments out of capture groups
            final Map<String, List<String>> args = new HashMap<>();

            // For each capture group, save the matching substring
            // into the parameter
            for (int i = 0; i < m.groupCount(); i++) {
              if (i < getParameters().size()) {
                final String currentParam = getParameters().get(i);

                if (!args.containsKey(currentParam)) {
                  args.put(currentParam, new ArrayList<>());
                }

                args.get(currentParam).add(m.group(i+1));
              }
            }

            // put the original but cleaned up root back onto the matched doc as the location stored in the partition
            final String locationCorpusPath = rootCleaned + fi;
            final OffsetDateTime lastModifiedTime =
                adapter.computeLastModifiedTimeAsync(locationCorpusPath).join();
            ((CdmLocalEntityDeclarationDefinition) getOwner()).createDataPartitionFromPattern(
                    locationCorpusPath, getExhibitsTraits(), args, getSpecializedSchema(), lastModifiedTime);
          }
        }
      }

      // update modified times
      setLastFileStatusCheckTime(OffsetDateTime.now(ZoneOffset.UTC));
    });
  }

  /**
   * Report most recent modified time (of current or children objects) to the parent object.
   */
  @Override
  public CompletableFuture<Void> reportMostRecentTimeAsync(final OffsetDateTime childTime) {
    if (getOwner() instanceof CdmFileStatus && childTime != null) {
      return ((CdmFileStatus) getOwner()).reportMostRecentTimeAsync(childTime);
    }

    return CompletableFuture.completedFuture(null);
  }
}