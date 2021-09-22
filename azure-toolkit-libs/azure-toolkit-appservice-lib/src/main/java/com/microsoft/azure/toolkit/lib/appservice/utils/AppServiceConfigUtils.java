/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.appservice.utils;

import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.appservice.AzureAppService;
import com.microsoft.azure.toolkit.lib.appservice.config.AppServiceConfig;
import com.microsoft.azure.toolkit.lib.appservice.config.RuntimeConfig;
import com.microsoft.azure.toolkit.lib.appservice.model.JavaVersion;
import com.microsoft.azure.toolkit.lib.appservice.model.OperatingSystem;
import com.microsoft.azure.toolkit.lib.appservice.service.IAppService;
import com.microsoft.azure.toolkit.lib.appservice.service.IAppServicePlan;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.legacy.appservice.AppServiceUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public class AppServiceConfigUtils {
    private static final String SETTING_DOCKER_IMAGE = "DOCKER_CUSTOM_IMAGE_NAME";
    private static final String SETTING_REGISTRY_SERVER = "DOCKER_REGISTRY_SERVER_URL";

    public static AppServiceConfig fromAppService(IAppService<?> webapp, IAppServicePlan servicePlan) {
        AppServiceConfig config = new AppServiceConfig();
        config.appName(webapp.name());

        config.resourceGroup(webapp.entity().getResourceGroup());
        config.subscriptionId(Utils.getSubscriptionId(webapp.id()));
        config.region(webapp.entity().getRegion());
        config.pricingTier(servicePlan.entity().getPricingTier());
        RuntimeConfig runtimeConfig = new RuntimeConfig();
        if (AppServiceUtils.isDockerAppService(webapp)) {
            runtimeConfig.os(OperatingSystem.DOCKER);
            final Map<String, String> settings = webapp.entity().getAppSettings();

            final String imageSetting = settings.get(SETTING_DOCKER_IMAGE);
            if (StringUtils.isNotBlank(imageSetting)) {
                runtimeConfig.image(imageSetting);
            } else {
                runtimeConfig.image(webapp.entity().getDockerImageName());
            }
            final String registryServerSetting = settings.get(SETTING_REGISTRY_SERVER);
            if (StringUtils.isNotBlank(registryServerSetting)) {
                runtimeConfig.registryUrl(registryServerSetting);
            }
        } else {
            runtimeConfig.os(webapp.getRuntime().getOperatingSystem());
            runtimeConfig.webContainer(webapp.getRuntime().getWebContainer());
            runtimeConfig.javaVersion(webapp.getRuntime().getJavaVersion());
        }
        config.runtime(runtimeConfig);
        if (servicePlan.entity() != null) {
            config.pricingTier(servicePlan.entity().getPricingTier());
            config.servicePlanName(servicePlan.name());
            config.servicePlanResourceGroup(servicePlan.entity().getResourceGroup());
        }
        return config;
    }

    public static AppServiceConfig buildDefaultConfig(String subscriptionId, String resourceGroup, String appName, String packaging, JavaVersion javaVersion) {
        final AppServiceConfig appServiceConfig = AppServiceConfig.buildDefaultWebAppConfig(resourceGroup, appName, packaging, javaVersion);
        final List<Region> regions = Azure.az(AzureAppService.class).listSupportedRegions(subscriptionId);
        // replace with first region when the default region is not present
        appServiceConfig.region(Utils.selectFirstOptionIfCurrentInvalid("region", regions, appServiceConfig.region()));
        return appServiceConfig;
    }

    public static void mergeAppServiceConfig(AppServiceConfig to, AppServiceConfig from) {
        try {
            mergeObjects(to, from);
        } catch (IllegalAccessException e) {
            throw new AzureToolkitRuntimeException("Cannot copy object for class AppServiceConfig.", e);
        }

        if (to.runtime() != from.runtime()) {
            mergeRuntime(to.runtime(), from.runtime());
        }
    }

    private static void mergeRuntime(RuntimeConfig to, RuntimeConfig from) {
        try {
            mergeObjects(to, from);
        } catch (IllegalAccessException e) {
            throw new AzureToolkitRuntimeException("Cannot copy object for class RuntimeConfig.", e);
        }
    }

    private static <T> void mergeObjects(T to, T from) throws IllegalAccessException {
        for (Field field : FieldUtils.getAllFields(to.getClass())) {
            if (FieldUtils.readField(field, to, true) == null) {
                final Object value = FieldUtils.readField(field, from, true);
                if (value != null) {
                    FieldUtils.writeField(field, to, value, true);
                }
            }

        }
    }
}
