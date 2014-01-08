package jenkins.plugins.hipchat;

import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;

public class HipChatServiceV2 implements HipChatService {
    private static final Logger logger = Logger.getLogger(StandardHipChatService.class.getName());
    private String endpoint = "https://api.hipchat.com/v2/room/%s/notification";
    private String token;
    private String room;

    public HipChatServiceV2(String token, String room) {
        this.token = token;
        this.room = room;
    }

    public void publish(String message) {
        makeRequest(token, room, message, "yellow");
    }

    public void publish(String message, String color) {
        makeRequest(token, room, message, color);
    }

    private String makeRequest(String token, String room, String message, String color) {
        logger.info("Posting: to " + room + ": " + message + " " + color);
        HttpClient client = getHttpClient();
        PostMethod post = new PostMethod(notificationUrl(token, room));

        try {
            StringRequestEntity body = new StringRequestEntity(
                    createMessage(message, color),
                    "application/json",
                    "UTF-8"
            );
            post.setRequestEntity(body);
            int responseCode = client.executeMethod(post);
            String response = post.getResponseBodyAsString();
            if(responseCode != HttpStatus.SC_NO_CONTENT) {
                logger.log(Level.WARNING, "HipChat post may have failed. Response: " + response);
            }
            return response;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error posting to HipChat", e);
        } finally {
            post.releaseConnection();
        }
        return null;
    }

    private String createMessage(String message, String color) {
        JSONObject notification = new JSONObject();
        notification.put("color", color);
        notification.put("message", message);
        notification.put("notify", false);
        notification.put("message_format", "html");
        return notification.toString();
    }

    private String notificationUrl(String token, String room) {
        return String.format(endpoint + "?auth_token=%s", room, token);
    }

    private HttpClient getHttpClient() {
        HttpClient client = new HttpClient();
        if (Jenkins.getInstance() != null) {
            ProxyConfiguration proxy = Jenkins.getInstance().proxy;
            if (proxy != null) {
                client.getHostConfiguration().setProxy(proxy.name, proxy.port);
            }
        }
        return client;
    }

}
