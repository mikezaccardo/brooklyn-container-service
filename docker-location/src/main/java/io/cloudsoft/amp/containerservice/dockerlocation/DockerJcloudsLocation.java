package io.cloudsoft.amp.containerservice.dockerlocation;

import java.util.List;

import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.Template;
import org.jclouds.docker.compute.options.DockerTemplateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * For provisioning docker containers, using the jclouds-docker integration.
 * 
 * This adds special support for default Cloudsoft images. If the image description matches our 
 * cloudsoft regexes, then auto-generate a password and pass that in as 
 * {@code CLOUDSOFT_ROOT_PASSWORD} when launching the container. That will then be used as the 
 * {@link DockerTemplateOptions#getLoginPassword()}.
 * 
 * Also, if no image is specified then this will set the default to "cloudsoft/centos:7"
 * (see https://hub.docker.com/r/cloudsoft/centos/).
 */
public class DockerJcloudsLocation extends JcloudsLocation {

    private static final Logger LOG = LoggerFactory.getLogger(DockerJcloudsLocation.class);

    public static final ConfigKey<Boolean> INJECT_LOGIN_CREDENTIAL = ConfigKeys.newBooleanConfigKey(
            "injectLoginCredential", 
            "Whether to inject login credentials (if null, will infer from image choice)", 
            null);

    public static final ConfigKey<String> DEFAULT_IMAGE_DESCRIPTION_REGEX = ConfigKeys.newStringConfigKey(
            "defaultImageDescriptionRegex", 
            "The default image description to use, if no other image preferences are supplied",
            "cloudsoft/centos:7");

    /**
     * The regex for the image descriptions that support us injecting login credentials.
     */
    private static final List<String> IMAGE_DESCRIPTION_REGEXES_REQUIRING_INJECTED_LOGIN_CREDS = ImmutableList.of(
            "cloudsoft/centos.*");

    @Override
    protected MachineLocation obtainOnce(ConfigBag setup) throws NoMachinesAvailableException {
        // Use the provider name that jclouds expects; rely on resolver to have validated this.
        setup.configure(JcloudsLocation.CLOUD_PROVIDER, "docker");
        
        // Inject default image, if absent
        String imageId = setup.get(JcloudsLocation.IMAGE_ID);
        String imageNameRegex = setup.get(JcloudsLocation.IMAGE_NAME_REGEX);
        String imageDescriptionRegex = setup.get(JcloudsLocation.IMAGE_DESCRIPTION_REGEX);
        String defaultImageDescriptionRegex = getConfig(DEFAULT_IMAGE_DESCRIPTION_REGEX);
        if (Strings.isBlank(imageId) && Strings.isBlank(imageNameRegex) && Strings.isBlank(imageDescriptionRegex)) {
            if (Strings.isNonBlank(defaultImageDescriptionRegex)) {
                LOG.debug("Setting default image regex to {}, for obtain call in {}", defaultImageDescriptionRegex, this);
                setup.configure(JcloudsLocation.IMAGE_DESCRIPTION_REGEX, defaultImageDescriptionRegex);
            }
        }
        
        return super.obtainOnce(setup);
    }
    
    @Override
    public Template buildTemplate(ComputeService computeService, ConfigBag config) {
        String loginUser = config.get(JcloudsLocation.LOGIN_USER);
        String loginPassword = config.get(JcloudsLocation.LOGIN_USER_PASSWORD);
        String loginKeyFile = config.get(JcloudsLocation.LOGIN_USER_PRIVATE_KEY_FILE);
        String loginKeyData = config.get(JcloudsLocation.LOGIN_USER_PRIVATE_KEY_DATA);

        Template template = super.buildTemplate(computeService, config);
        Image image = template.getImage();

        // Inject login credentials, if required
        Boolean injectLoginCredentials = config.get(INJECT_LOGIN_CREDENTIAL);
        if (injectLoginCredentials == null) {
            String imageDescription = image.getDescription();
            for (String regex : IMAGE_DESCRIPTION_REGEXES_REQUIRING_INJECTED_LOGIN_CREDS) {
                if (imageDescription != null && imageDescription.matches(regex)) {
                    injectLoginCredentials = true;
                    break;
                }
            }
        }
        if (Strings.isBlank(loginUser) && Strings.isBlank(loginPassword) && Strings.isBlank(loginKeyFile) && Strings.isBlank(loginKeyData)) {
            if (Boolean.TRUE.equals(injectLoginCredentials)) {
                loginUser = "root";
                loginPassword = Identifiers.makeRandomPassword(12);
                DockerTemplateOptions templateOptions = (DockerTemplateOptions) template.getOptions();
                templateOptions.overrideLoginUser(loginUser);
                templateOptions.overrideLoginPassword(loginPassword);
                
                List<String> origEnv = templateOptions.getEnv();
                templateOptions.env(MutableList.<String>builder()
                        .addAll(origEnv != null ? origEnv : ImmutableList.<String>of())
                        .add("CLOUDSOFT_ROOT_PASSWORD="+loginPassword)
                        .build());
            }
        }

        return template;
    }
}