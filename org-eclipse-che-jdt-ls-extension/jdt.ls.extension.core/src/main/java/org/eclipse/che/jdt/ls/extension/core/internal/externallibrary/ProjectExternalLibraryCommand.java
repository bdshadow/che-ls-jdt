/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.jdt.ls.extension.core.internal.externallibrary;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;
import org.eclipse.che.jdt.ls.extension.api.dto.ExternalLibrariesParameters;
import org.eclipse.che.jdt.ls.extension.api.dto.Jar;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.lsp4j.jsonrpc.json.adapters.CollectionTypeAdapterFactory;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapterFactory;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EnumTypeAdapterFactory;

/**
 * A command to compute dependencies of project.
 *
 * @author Valeriy Svydenko
 */
public class ProjectExternalLibraryCommand {
  private static final Gson gson =
      new GsonBuilder()
          .registerTypeAdapterFactory(new CollectionTypeAdapterFactory())
          .registerTypeAdapterFactory(new EitherTypeAdapterFactory())
          .registerTypeAdapterFactory(new EnumTypeAdapterFactory())
          .create();

  /**
   * Gets list of project dependencies.
   *
   * @param parameters first parameter must be of type {@link ExternalLibrariesParameters} which
   *     contains project URI
   * @param pm a progress monitor
   * @return list of jars {@link Jar}
   */
  public static List<Jar> execute(List<Object> parameters, IProgressMonitor pm) {
    ExternalLibrariesParameters params =
        gson.fromJson(gson.toJson(parameters.get(0)), ExternalLibrariesParameters.class);
    try {
      return LibraryNavigation.getProjectDependencyJars(params.getProjectUri(), pm);
    } catch (JavaModelException e) {
      throw new RuntimeException(e);
    }
  }
}
