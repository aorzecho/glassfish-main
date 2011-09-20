package org.glassfish.elasticity.engine.container;

import org.glassfish.elasticity.api.Alert;
import org.glassfish.elasticity.api.AlertContext;
import org.glassfish.elasticity.config.serverbeans.AlertConfig;
import org.glassfish.elasticity.config.serverbeans.ElasticService;
import org.glassfish.elasticity.engine.util.EngineUtil;
import org.glassfish.elasticity.group.ElasticMessageHandler;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;

/**
 * @author Mahesh.Kannan@Oracle.Com
 */
public class AlertContextImpl<C extends AlertConfig>
        implements AlertContext<C>, Runnable {

    private ElasticServiceContainer elasticServiceContainer;

    private C config;

    private Alert alert;

    private ScheduledFuture<?> future;

    private Map transientData = new HashMap();

    private String alertToken;

    public AlertContextImpl(ElasticServiceContainer elasticServiceContainer, C config, Alert alert) {
        this.elasticServiceContainer = elasticServiceContainer;
        this.config = config;
        this.alert = alert;
    }

    public ElasticService getElasticService() {
        return elasticServiceContainer.getElasticService();
    }

    public ElasticServiceContainer getElasticServiceContainer() {
        return elasticServiceContainer;
    }

    public C getAlertConfig() {
        return config;
    }

    public Alert getAlert() {
        return alert;
    }

    public ScheduledFuture<?> getFuture() {
        return future;
    }

    public void setFuture(ScheduledFuture<?> future) {
        this.future = future;
    }

    public Map getTransientData() {
        return transientData;
    }

    public void run() {
        try {
            Alert.AlertState state = alert.execute(this);
            System.out.println("Alert returned STATE = " + state);
            switch (state) {
                case NO_DATA:
                    break;
                case OK:
                    elasticServiceContainer.scaleDown();
                case ALARM:
                    elasticServiceContainer.scaleUp();
                    break;
            }
            transientData.clear();
        } catch (Exception ex) {
            EngineUtil.getLogger().log(Level.WARNING, "Exception during Alert execution", ex);
        }
    }

}
