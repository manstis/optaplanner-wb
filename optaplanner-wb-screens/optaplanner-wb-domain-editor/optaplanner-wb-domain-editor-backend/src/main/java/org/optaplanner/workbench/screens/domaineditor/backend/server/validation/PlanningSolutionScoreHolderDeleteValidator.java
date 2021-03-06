/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.workbench.screens.domaineditor.backend.server.validation;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.guvnor.common.services.shared.message.Level;
import org.guvnor.common.services.shared.validation.model.ValidationMessage;
import org.kie.workbench.common.screens.datamodeller.model.GenerationResult;
import org.kie.workbench.common.screens.datamodeller.service.DataModelerService;
import org.kie.workbench.common.services.datamodeller.core.DataObject;
import org.kie.workbench.common.services.refactoring.service.AssetsUsageService;
import org.kie.workbench.common.services.refactoring.service.ResourceType;
import org.kie.workbench.common.services.shared.validation.DeleteValidator;
import org.optaplanner.workbench.screens.domaineditor.validation.ScoreHolderGlobalToBeRemovedMessage;
import org.optaplanner.workbench.screens.domaineditor.validation.ScoreHolderGlobalTypeNotRecognizedMessage;
import org.uberfire.backend.server.util.Paths;
import org.uberfire.backend.vfs.Path;
import org.uberfire.io.IOService;

import static org.optaplanner.workbench.screens.domaineditor.model.PlannerDomainAnnotations.PLANNING_SOLUTION_ANNOTATION;

/**
 * Check whether data object to be deleted is a Planning Solution. Display warning message as 'scoreHolder' global variable
 * associated with a score type defined in the Planning Solution will be deleted as a consequence. This breaks all
 * the rules where the 'scoreHolder' global variable is referenced.
 */
@ApplicationScoped
public class PlanningSolutionScoreHolderDeleteValidator implements DeleteValidator<DataObject> {

    private DataModelerService dataModelerService;

    private IOService ioService;

    private ScoreHolderUtils scoreHolderUtils;

    private AssetsUsageService assetsUsageService;

    @Inject
    public PlanningSolutionScoreHolderDeleteValidator(final DataModelerService dataModelerService,
                                                      @Named("ioStrategy") final IOService ioService,
                                                      final ScoreHolderUtils scoreHolderUtils,
                                                      final AssetsUsageService assetsUsageService) {
        this.dataModelerService = dataModelerService;
        this.ioService = ioService;
        this.scoreHolderUtils = scoreHolderUtils;
        this.assetsUsageService = assetsUsageService;
    }

    @Override
    public Collection<ValidationMessage> validate(final Path dataObjectPath,
                                                  final DataObject dataObject) {
        return validatePath(dataObjectPath);
    }

    @Override
    public Collection<ValidationMessage> validate(final Path dataObjectPath) {
        return validatePath(dataObjectPath);
    }

    private Collection<ValidationMessage> validatePath(final Path dataObjectPath) {
        if (dataObjectPath != null) {
            String dataObjectSource = ioService.readAllString(Paths.convert(dataObjectPath));
            GenerationResult generationResult = dataModelerService.loadDataObject(dataObjectPath,
                                                                                  dataObjectSource,
                                                                                  dataObjectPath);
            if (generationResult.hasErrors()) {
                return Collections.emptyList();
            } else {
                DataObject originalDataObject = generationResult.getDataObject();

                if (originalDataObject.getAnnotation(PLANNING_SOLUTION_ANNOTATION) != null) {
                    String originalDataObjectScoreTypeFqn = scoreHolderUtils.extractScoreTypeFqn(originalDataObject);

                    String originalDataObjectScoreHolderTypeFqn = scoreHolderUtils.getScoreHolderTypeFqn(originalDataObjectScoreTypeFqn);

                    if (originalDataObjectScoreHolderTypeFqn == null) {
                        return Arrays.asList(new ScoreHolderGlobalTypeNotRecognizedMessage(Level.WARNING));
                    }

                    List<Path> scoreHolderGlobalUsages = assetsUsageService.getAssetUsages(originalDataObjectScoreHolderTypeFqn,
                                                                                           ResourceType.JAVA,
                                                                                           dataObjectPath);
                    if (scoreHolderGlobalUsages.isEmpty()) {
                        return Collections.emptyList();
                    } else {
                        return Arrays.asList(new ScoreHolderGlobalToBeRemovedMessage(Level.WARNING));
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    @Override
    public boolean accept(final Path path) {
        return path.getFileName().endsWith(".java");
    }
}
