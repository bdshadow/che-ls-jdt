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
package org.eclipse.che.jdt.ls.extension.core.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.che.jdt.ls.extension.api.dto.ExtendedSymbolInformation;
import org.eclipse.che.jdt.ls.extension.api.dto.FileStructureCommandParameters;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentSymbolHandler;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.json.adapters.CollectionTypeAdapterFactory;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapterFactory;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EnumTypeAdapterFactory;

/**
 * A command to compute a file structure tree.
 *
 * @author Thomas Mäder
 */
public class FileStructureCommand {
  private static final Gson gson =
      new GsonBuilder()
          .registerTypeAdapterFactory(new CollectionTypeAdapterFactory())
          .registerTypeAdapterFactory(new EitherTypeAdapterFactory())
          .registerTypeAdapterFactory(new EnumTypeAdapterFactory())
          .create();

  /**
   * Compute the file structure hierarchy
   *
   * @param parameters First parameter must be of type FileStructureCommandParameters
   * @param pm a progress monitor
   * @return fully rendered hierarchy of symbols. Parent names are emtpy.
   */
  public static List<ExtendedSymbolInformation> execute(
      List<Object> parameters, IProgressMonitor pm) {
    List<ExtendedSymbolInformation> infos = new ArrayList<ExtendedSymbolInformation>();
    FileStructureCommandParameters params =
        gson.fromJson(gson.toJson(parameters.get(0)), FileStructureCommandParameters.class);
    boolean showInherited = params.getShowInherited();
    ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(params.getUri());
    try {
      for (IJavaElement element : typeRoot.getChildren()) {
        if (element instanceof IType) {
          infos.add(
              createSymbolInfo(
                  element,
                  null,
                  JavaElementLabels.getElementLabel(element, JavaElementLabels.ALL_DEFAULT),
                  showInherited,
                  pm));
        }
      }
    } catch (JavaModelException e) {
      throw new RuntimeException(e);
    }

    return infos;
  }

  private static ExtendedSymbolInformation createSymbolInfo(
      IJavaElement element,
      IJavaElement parent,
      String label,
      boolean showInherited,
      IProgressMonitor pm)
      throws JavaModelException {

    if (pm.isCanceled()) {
      throw new OperationCanceledException();
    }
    ExtendedSymbolInformation result = new ExtendedSymbolInformation();

    Location location = JDTUtils.toLocation(element);
    if (location != null) {
      SymbolInformation si = new SymbolInformation();
      si.setName(label);
      si.setKind(mapKind(element));
      location.setUri(ResourceUtils.toClientUri(location.getUri()));
      si.setLocation(location);
      result.setInfo(si);
      List<ExtendedSymbolInformation> children = new ArrayList<>();
      if (element instanceof IParent) {
        Set<String> found = new HashSet<>();
        for (IJavaElement child : ((IParent) element).getChildren()) {
          if (!found.contains(child.getHandleIdentifier())) {
            found.add(child.getHandleIdentifier());
            children.add(
                createSymbolInfo(
                    child,
                    element,
                    JavaElementLabels.getElementLabel(child, JavaElementLabels.ALL_DEFAULT),
                    showInherited,
                    pm));
          }
        }
        if (showInherited && parent == null && element instanceof IType) {
          IType type = (IType) element;
          ITypeHierarchy th = type.newSupertypeHierarchy(pm);
          for (IType superType : th.getAllSupertypes(type)) {
            for (IJavaElement child : superType.getChildren()) {
              if (!(child instanceof IInitializer)
                  && !found.contains(child.getHandleIdentifier())) {
                found.add(child.getHandleIdentifier());
                children.add(
                    createSymbolInfo(
                        child,
                        superType,
                        JavaElementLabels.getElementLabel(
                            child, JavaElementLabels.DEFAULT_POST_QUALIFIED),
                        showInherited,
                        pm));
              }
            }
          }
        }
      }
      result.setChildren(children);
    }

    return result;
  }

  private static SymbolKind mapKind(IJavaElement element) {
    if (element.getElementType() == IJavaElement.METHOD) {
      // workaround for https://github.com/eclipse/eclipse.jdt.ls/issues/422
      return SymbolKind.Method;
    }
    return DocumentSymbolHandler.mapKind(element);
  }
}
