package org.openhab.binding.plum.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openhab.binding.plum.PlumBindingConfig;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlumMotionWatchdog implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(PlumMotionWatchdog.class);
    private EventPublisher eventPublisher = null;
    private Map<PlumBindingConfig, Long> watchdog = new HashMap<PlumBindingConfig, Long>();

    public PlumMotionWatchdog(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    private void publishState(PlumBindingConfig c, State state) {
        eventPublisher.postUpdate(c.getName(), state);
    }

    public void updateWatchdog(PlumBindingConfig c) {
        watchdog.put(c, System.currentTimeMillis());
    }

    @Override
    public void run() {
        logger.info("Starting Plum motion sensor watchdog");
        while (!Thread.interrupted()) {
            try {
                List<PlumBindingConfig> deletes = new ArrayList<PlumBindingConfig>();
                for (Entry<PlumBindingConfig, Long> w : watchdog.entrySet()) {
                    if (w.getKey().getType().equals("motion")) {
                        if (System.currentTimeMillis() - w.getValue() > 5000) {
                            logger.info("Plum motion sensor watchdog: closing " + w.getKey().getName()
                                    + " sensor after 5 second idle");
                            deletes.add(w.getKey());
                            publishState(w.getKey(), OpenClosedType.CLOSED);
                        }
                    }
                }
                for (PlumBindingConfig d : deletes) {
                    watchdog.remove(d);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.info("Stopping Plum motion sensor watchdog");
                break;
            }
        }
    }

    public boolean isNotRunning(PlumBindingConfig config) {
        return !watchdog.containsKey(config);
    }

}
