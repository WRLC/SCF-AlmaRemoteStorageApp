package com.exlibris.version;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.apache.log4j.Logger;

@WebListener
public class AppStartupListener implements ServletContextListener {

    private static final Logger logger = Logger.getLogger(AppStartupListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.info(AppVersionInfo.getInstance().toLogLine());
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Nothing to release.
    }
}
