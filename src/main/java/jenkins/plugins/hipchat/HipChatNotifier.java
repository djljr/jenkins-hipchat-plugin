package jenkins.plugins.hipchat;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

@SuppressWarnings({"unchecked"})
public class HipChatNotifier extends Notifier {

    private static final Logger logger = Logger.getLogger(HipChatNotifier.class.getName());

    private String apiVersion;

    private String authToken;
    private String buildServerUrl;
    private String room;
    private String sendAs;

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getRoom() {
        return room;
    }

    public String getAuthToken() {
        return authToken;
    }

    public String getBuildServerUrl() {
        return buildServerUrl;
    }

    public String getSendAs() {
        return sendAs;
    }


    @DataBoundConstructor
    public HipChatNotifier(final String authToken, final String room, String buildServerUrl, final String sendAs) {
        super();
        this.authToken = authToken;
        this.buildServerUrl = buildServerUrl;
        this.room = room;
        this.sendAs = sendAs;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    public HipChatService newHipChatService(final String room) {
        return new StandardHipChatService(getAuthToken(), room == null ? getRoom() : room, StringUtils.isBlank(getSendAs()) ? "Build Server" : getSendAs());
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        private String apiVersion;
        private String token;
        private String room;
        private String buildServerUrl;
        private String sendAs;

        public DescriptorImpl() {
            load();
        }

        public String getApiVersion() {
            return apiVersion;
        }

        public String getToken() {
            return token;
        }

        public String getRoom() {
            return room;
        }

        public String getBuildServerUrl() {
            return buildServerUrl;
        }

        public String getSendAs() {
            return sendAs;
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public FormValidation doSendTestNotificationV1(@QueryParameter("hipChatToken") final String token,
                                                       @QueryParameter("hipChatRoom") final String room,
                                                       @QueryParameter("hipChatSendAs") final String sendAs) {
            try {
                new StandardHipChatService(token, room, sendAs).publish("Testing " + new Date().toString(), "purple");
                return FormValidation.ok("Sent. Look for a purple message in the room [" + room + "] from [" + sendAs + "]");
            } catch (Exception e) {
                return FormValidation.error("Got Exception: " + e.getMessage());
            }
        }

        public FormValidation doSendTestNotificationV2(@QueryParameter("hipChatToken") final String token,
                                                       @QueryParameter("hipChatRoom") final String room) {
            try {
                new HipChatServiceV2(token, room).publish("Testing " + new Date().toString(), "purple");
                return FormValidation.ok("Sent. Look for a purple message in the room [" + room + "]");
            } catch (Exception e) {
                return FormValidation.error("Got Exception: " + e.getMessage());
            }
        }

        @Override
        public HipChatNotifier newInstance(StaplerRequest sr) {
            if (apiVersion == null) apiVersion = sr.getParameter("hipChatApiVersion");
            if (token == null) token = sr.getParameter("hipChatToken");
            if (buildServerUrl == null) buildServerUrl = sr.getParameter("hipChatBuildServerUrl");
            if (room == null) room = sr.getParameter("hipChatRoom");
            if (sendAs == null) sendAs = sr.getParameter("hipChatSendAs");
            return new HipChatNotifier(token, room, buildServerUrl, sendAs);
        }

        @Override
        public boolean configure(StaplerRequest sr, JSONObject formData) throws FormException {
            apiVersion = formData.getString("hipChatApiVersion");
            buildServerUrl = formData.getString("hipChatBuildServerUrl");
            if (buildServerUrl != null && !buildServerUrl.endsWith("/")) {
                buildServerUrl = buildServerUrl + "/";
            }

            if (ObjectUtils.equals(apiVersion, "v1")) {
                token = formData.getString("hipChatToken");
                room = formData.getString("hipChatRoom");
                sendAs = formData.getString("hipChatSendAs");
            }
            else if(ObjectUtils.equals(apiVersion, "v2")) {
                token = formData.getString("hipChatToken");
                room = formData.getString("hipChatRoom");
            }

            try {
                new HipChatNotifier(token, room, buildServerUrl, sendAs);
            } catch (Exception e) {
                throw new FormException("Failed to initialize notifier - check your global notifier configuration settings", e, "");
            }
            save();
            return super.configure(sr, formData);
        }

        @Override
        public String getDisplayName() {
            return "HipChat Notifications";
        }
    }

    public static class HipChatJobProperty extends hudson.model.JobProperty<AbstractProject<?, ?>> {
        private String room;
        private boolean startNotification;
        private boolean notifySuccess;
        private boolean notifyAborted;
        private boolean notifyNotBuilt;
        private boolean notifyUnstable;
        private boolean notifyFailure;
        private boolean notifyBackToNormal;


        @DataBoundConstructor
        public HipChatJobProperty(String room,
                                  boolean startNotification,
                                  boolean notifyAborted,
                                  boolean notifyFailure,
                                  boolean notifyNotBuilt,
                                  boolean notifySuccess,
                                  boolean notifyUnstable,
                                  boolean notifyBackToNormal) {
            this.room = room;
            this.startNotification = startNotification;
            this.notifyAborted = notifyAborted;
            this.notifyFailure = notifyFailure;
            this.notifyNotBuilt = notifyNotBuilt;
            this.notifySuccess = notifySuccess;
            this.notifyUnstable = notifyUnstable;
            this.notifyBackToNormal = notifyBackToNormal;
        }

        @Exported
        public String getRoom() {
            return room;
        }

        @Exported
        public boolean getStartNotification() {
            return startNotification;
        }

        @Exported
        public boolean getNotifySuccess() {
            return notifySuccess;
        }

        @Override
        public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
            if (startNotification) {
                Map<Descriptor<Publisher>, Publisher> map = build.getProject().getPublishersList().toMap();
                for (Publisher publisher : map.values()) {
                    if (publisher instanceof HipChatNotifier) {
                        logger.info("Invoking Started...");
                        new ActiveNotifier((HipChatNotifier) publisher).started(build);
                    }
                }
            }
            return super.prebuild(build, listener);
        }

        @Exported
        public boolean getNotifyAborted() {
            return notifyAborted;
        }

        @Exported
        public boolean getNotifyFailure() {
            return notifyFailure;
        }

        @Exported
        public boolean getNotifyNotBuilt() {
            return notifyNotBuilt;
        }

        @Exported
        public boolean getNotifyUnstable() {
            return notifyUnstable;
        }

        @Exported
        public boolean getNotifyBackToNormal() {
            return notifyBackToNormal;
        }

        @Extension
        public static final class DescriptorImpl extends JobPropertyDescriptor {
            public String getDisplayName() {
                return "HipChat Notifications";
            }

            @Override
            public boolean isApplicable(Class<? extends Job> jobType) {
                return true;
            }

            @Override
            public HipChatJobProperty newInstance(StaplerRequest sr, JSONObject formData) throws hudson.model.Descriptor.FormException {
                return new HipChatJobProperty(sr.getParameter("hipChatProjectRoom"),
                        sr.getParameter("hipChatStartNotification") != null,
                        sr.getParameter("hipChatNotifyAborted") != null,
                        sr.getParameter("hipChatNotifyFailure") != null,
                        sr.getParameter("hipChatNotifyNotBuilt") != null,
                        sr.getParameter("hipChatNotifySuccess") != null,
                        sr.getParameter("hipChatNotifyUnstable") != null,
                        sr.getParameter("hipChatNotifyBackToNormal") != null);
            }
        }
    }
}
