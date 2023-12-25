// SPDX-License-Identifier: MIT

package lermitage.intellij.extra.icons.activity;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import lermitage.intellij.extra.icons.BaseIconProvider;
import lermitage.intellij.extra.icons.lic.ExtraIconsLicenseCheck;
import lermitage.intellij.extra.icons.lic.ExtraIconsLicenseStatus;
import lermitage.intellij.extra.icons.lic.ExtraIconsPluginType;
import lermitage.intellij.extra.icons.messaging.RefreshIconsNotifierService;
import lermitage.intellij.extra.icons.utils.I18nUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

/**
 * Check licence periodically.
 */
public class LicCheckIDEActivity implements ProjectActivity {

    private static final @NonNls Logger LOGGER = Logger.getInstance(BaseIconProvider.class);

    private static final ResourceBundle i18n = I18nUtils.getResourceBundle();

    private static boolean started = false; // TODO start once per IDE, not per project opening

    private static boolean requestLicenseShown = false;

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        if (started) {
            return null; // workaround in order to keep only once timer per IDE instead of one timer per project opening
        }
        started = true;
        LOGGER.info("Started Extra Icons license checker");

        int check_delay_1 = 60_000; // 1 min
        int check_delay_2 = 3_600_000; // 1 hr
        int check_period = 3 * 3_600_000; // 3 hrs
        if ("true".equals(System.getenv("EXTRA_ICONS_TEST_MODE"))) {
            check_delay_1 = 3_000; // 3 sec
            check_delay_2 = 30_000; // 30 sec
            check_period = 180_000; // 3 min
        }

        long t1 = System.currentTimeMillis();
        ExtraIconsPluginType installedPluginType = findInstalledPluginType();
        long t2 = System.currentTimeMillis();
        LOGGER.info("Found Extra Icons configured for license type in " + (t2 - t1) + " ms: " + installedPluginType);

        if (installedPluginType.isRequiresLicense()) {
            try {
                ExtraIconsLicenseStatus.setLicenseActivated(true);
                LOGGER.info("Will check Extra Icons license in " + check_delay_1 / 1000 + " sec, " +
                    "in " + check_delay_2 / 1000 + " sec, " +
                    "then every " + check_period / 1000 + " sec");
                new Timer().schedule(createLicenseCheckerTimerTask(installedPluginType), check_delay_1);
                new Timer().scheduleAtFixedRate(createLicenseCheckerTimerTask(installedPluginType), check_delay_2, check_period);
            } catch (Exception e) {
                LOGGER.warn(e);
            }
        } else {
            ExtraIconsLicenseStatus.setLicenseActivated(true);
        }
        return null;
    }

    private TimerTask createLicenseCheckerTimerTask(ExtraIconsPluginType installedPluginType) {
        return new TimerTask() {
            @Override
            public void run() {
                long t1 = System.currentTimeMillis();
                Boolean isLicensed = ExtraIconsLicenseCheck.isLicensed(installedPluginType.getProductCode());
                long t2 = System.currentTimeMillis();
                LOGGER.info("Checked Extra Icons license in " + (t2 - t1) + " ms. User has a valid license: " + isLicensed);
                if (isLicensed == null) {
                    LOGGER.warn("Extra Icons license check returned null. Let's consider user has a valid license, " +
                        "and retry license check later");
                }
                if (isLicensed != null && !isLicensed) {
                    ExtraIconsLicenseStatus.setLicenseActivated(false);
                    LOGGER.warn("Failed to validate Extra Icons license. Disable all Extra Icons until license activation");
                    RefreshIconsNotifierService.getInstance().triggerAllIconsRefreshAndIconEnablersReinit();
                    if (!requestLicenseShown) {
                        requestLicenseShown = true;
                        ExtraIconsLicenseCheck.requestLicense(installedPluginType.getProductCode(), i18n.getString("license.required.msg"));
                    }
                }
            }
        };
    }

    private ExtraIconsPluginType findInstalledPluginType() {
        PluginDescriptor pluginDesc = PluginManager.getPluginByClass(LicCheckIDEActivity.class);
        if (pluginDesc == null) {
            LOGGER.warn("Failed to find installed Extra Icons plugin by class, will list all installed plugins and try to find it");
            Set<String> registeredIds = PluginId.getRegisteredIds().stream()
                .map(PluginId::getIdString)
                .collect(Collectors.toSet());
            Optional<ExtraIconsPluginType> extraIconsPluginTypeFound = ExtraIconsPluginType.FINDABLE_TYPES.stream()
                .filter(extraIconsPluginType -> registeredIds.contains(extraIconsPluginType.getPluginId()))
                .findFirst();
            return extraIconsPluginTypeFound.orElse(ExtraIconsPluginType.NOT_FOUND);
        } else {
            LOGGER.info("Found installed Extra Icons plugin by class: " + pluginDesc);
            String installedPluginId = pluginDesc.getPluginId().getIdString();
            Optional<ExtraIconsPluginType> extraIconsPluginTypeFound = ExtraIconsPluginType.FINDABLE_TYPES.stream()
                .filter(extraIconsPluginType -> installedPluginId.equals(extraIconsPluginType.getPluginId()))
                .findFirst();
            return extraIconsPluginTypeFound.orElse(ExtraIconsPluginType.NOT_FOUND);
        }
    }
}
