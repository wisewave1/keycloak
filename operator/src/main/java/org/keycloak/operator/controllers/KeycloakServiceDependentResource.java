/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.operator.controllers;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import org.keycloak.operator.Constants;
import org.keycloak.operator.crds.v2alpha1.deployment.Keycloak;
import org.keycloak.operator.crds.v2alpha1.deployment.spec.HttpSpec;

import java.util.Optional;
import java.util.function.Function;

import static org.keycloak.operator.crds.v2alpha1.CRDUtils.isTlsConfigured;

@KubernetesDependent(labelSelector = Constants.DEFAULT_LABELS_AS_STRING, resourceDiscriminator = KeycloakServiceDependentResource.NameResourceDiscriminator.class)
public class KeycloakServiceDependentResource extends CRUDKubernetesDependentResource<Service, Keycloak> {

    public static class NameResourceDiscriminator implements ResourceDiscriminator<Service, Keycloak> {
        @Override
        public Optional<Service> distinguish(Class<Service> resource, Keycloak primary, Context<Keycloak> context) {
            return getService(KeycloakServiceDependentResource::getServiceName, primary, context);
        }
    }

    public static Optional<Service> getService(Function<Keycloak, String> nameFunction, Keycloak primary, Context<Keycloak> context) {
        InformerEventSource<Service, Keycloak> ies = (InformerEventSource<Service, Keycloak>) context
                .eventSourceRetriever().getResourceEventSourceFor(Service.class);

        return ies.get(new ResourceID(nameFunction.apply(primary), primary.getMetadata().getNamespace()));
    }

    public KeycloakServiceDependentResource() {
        super(Service.class);
    }

    private ServiceSpec getServiceSpec(Keycloak keycloak) {
        String name = isTlsConfigured(keycloak) ? Constants.KEYCLOAK_HTTPS_PORT_NAME : Constants.KEYCLOAK_HTTP_PORT_NAME;
        return new ServiceSpecBuilder()
              .addNewPort()
              .withPort(getServicePort(keycloak))
              .withName(name)
              .withProtocol(Constants.KEYCLOAK_SERVICE_PROTOCOL)
              .endPort()
              .withSelector(OperatorManagedResource.allInstanceLabels(keycloak))
              .build();
    }

    @Override
    protected Service desired(Keycloak primary, Context<Keycloak> context) {
        Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(getServiceName(primary))
                .withNamespace(primary.getMetadata().getNamespace())
                .addToLabels(OperatorManagedResource.allInstanceLabels(primary))
                .endMetadata()
                .withSpec(getServiceSpec(primary))
                .build();
        return service;
    }

    public static String getServiceName(HasMetadata keycloak) {
        return keycloak.getMetadata().getName() + Constants.KEYCLOAK_SERVICE_SUFFIX;
    }

    public static int getServicePort(Keycloak keycloak) {
        // we assume HTTP when TLS is not configured
        if (!isTlsConfigured(keycloak)) {
            return Optional.ofNullable(keycloak.getSpec().getHttpSpec()).map(HttpSpec::getHttpPort).orElse(Constants.KEYCLOAK_HTTP_PORT);
        } else {
            return Optional.ofNullable(keycloak.getSpec().getHttpSpec()).map(HttpSpec::getHttpsPort).orElse(Constants.KEYCLOAK_HTTPS_PORT);
        }
    }
}
