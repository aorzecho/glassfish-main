/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.admin.rest.composite.resource;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;
import javax.ws.rs.core.UriInfo;
import org.glassfish.admin.rest.composite.CompositeResource;
import org.glassfish.admin.rest.composite.RestCollection;
import org.glassfish.admin.rest.composite.metadata.HelpText;
import org.glassfish.admin.rest.model.BaseModel;

/**
 *
 * @author jdlee
 */
public class DummiesResource extends CompositeResource {

    @Override
    public UriInfo getUriInfo() {
        return new UriInfo() {

            @Override
            public String getPath() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public String getPath(boolean decode) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public List<PathSegment> getPathSegments() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public List<PathSegment> getPathSegments(boolean decode) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public URI getRequestUri() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public UriBuilder getRequestUriBuilder() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public URI getAbsolutePath() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public UriBuilder getAbsolutePathBuilder() {
                return new UriBuilder() {

                    @Override
                    public UriBuilder clone() {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder uri(URI uri) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder uri(String uriTemplate) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder scheme(String scheme) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder schemeSpecificPart(String ssp) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder userInfo(String ui) {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder host(String host) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder port(int port) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder replacePath(String path) {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder path(String path) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder path(Class<?> resource) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder path(Class<?> resource, String method) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder path(Method method) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder segment(String... segments) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder replaceMatrix(String matrix) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder matrixParam(String name, Object... values) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder replaceMatrixParam(String name, Object... values) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder replaceQuery(String query) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder queryParam(String name, Object... values) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder replaceQueryParam(String name, Object... values) throws IllegalArgumentException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public UriBuilder fragment(String fragment) {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public URI buildFromMap(Map<String, ? extends Object> values) throws IllegalArgumentException, UriBuilderException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public URI buildFromEncodedMap(Map<String, ? extends Object> values) throws IllegalArgumentException, UriBuilderException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public URI build(Object... values) throws IllegalArgumentException, UriBuilderException {
                        URI uri = null;
                        try {
                            uri = new URI("");
                        } catch (URISyntaxException ex) {
                            Logger.getLogger(DummiesResource.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        return uri;
                    }

                    @Override
                    public URI buildFromEncoded(Object... values) throws IllegalArgumentException, UriBuilderException {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                };
            }

            @Override
            public URI getBaseUri() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public UriBuilder getBaseUriBuilder() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public MultivaluedMap<String, String> getPathParameters() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public MultivaluedMap<String, String> getPathParameters(boolean decode) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public MultivaluedMap<String, String> getQueryParameters() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public MultivaluedMap<String, String> getQueryParameters(boolean decode) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public List<String> getMatchedURIs() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public List<String> getMatchedURIs(boolean decode) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public List<Object> getMatchedResources() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

        };
    }

    @GET
    public RestCollection<BaseModel> getDummyDataCollection(
            @QueryParam("type") @HelpText(bundle="org.glassfish.admin.rest.composite.HelpText", key="dummy.type") String type
            ) {
        RestCollection<BaseModel> rc = new RestCollection<BaseModel>();

        return rc;
    }

    @Path("id/{name}")
    public DummyResource getDummyData(@QueryParam("foo") String foo) {
        return getSubResource(DummyResource.class);
    }

    @POST
    public Response createDummy(BaseModel model) {
        return Response.ok().build();
    }
}
