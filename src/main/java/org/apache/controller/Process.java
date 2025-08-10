package org.apache.controller;

import org.apache.logging.CollectLogger;
import org.apache.model.Release;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

public class Process implements Runnable {
    private final String targetName;
    private final String urlproject;
    private final CountDownLatch latch;
    private final String threadIdentity;
    private static final Logger logger = CollectLogger.getInstance().getLogger();

    public Process( int threadId,CountDownLatch latch,String targetName, String urlproject) {
        this.targetName = targetName;
        this.urlproject = urlproject;
        this.latch = latch;
        this.threadIdentity = "Thread-" + threadId + " (" + targetName + ")";

    }
    @Override
    public void run() {
        long startTime = System.currentTimeMillis();
        this.processing();
        long endTime = System.currentTimeMillis();
        String message = this.threadIdentity+ CollectLogger.ELAPSED_TIME+((endTime - startTime)/Math.pow(10,6))+ CollectLogger.SECONDS;
        logger.info(message);
        this.latch.countDown();
    }
    private void processing(){
        long overallStartTime = System.currentTimeMillis();
        try{
            /****************JIRA**********************/
            long start= System.currentTimeMillis();
            Jira jira = new Jira(this.targetName);
            jira.injectRelease();
            long end= System.currentTimeMillis();
            String message = this.threadIdentity + CollectLogger.ELAPSED_TIME + ((end - start) / Math.pow(10, 6)) + CollectLogger.SECONDS;
            logger.info(message);
            /****************RELEASE**********************/
            long startRelease = System.currentTimeMillis();
            List<Release> releases= jira.getRealeases();
            long endRelease = System.currentTimeMillis();
            String releaseMessage = this.threadIdentity + CollectLogger.ELAPSED_TIME + ((endRelease - startRelease) / Math.pow(10, 6)) + CollectLogger.SECONDS;
            logger.info(releaseMessage);
            /****************GIT**********************/
            long startGit = System.currentTimeMillis();
            Git git = new Git(this.targetName, this.urlproject,releases);
            git.injectCommits();
            long endGit = System.currentTimeMillis();
            String gitMessage = this.threadIdentity + CollectLogger.ELAPSED_TIME + ((endGit-startGit)/Math.pow(10,6))+CollectLogger.SECONDS;
            logger.info(gitMessage);

            long startCommits = System.currentTimeMillis();
            jira.injectTickets();
            git.setTickets(jira.getFixedTickets());
            git.processCommitsWithIssues();
            long endCommits = System.currentTimeMillis();
            String commitMessage = this.threadIdentity + CollectLogger.ELAPSED_TIME + ((endCommits - startCommits) / Math.pow(10, 6)) + CollectLogger.SECONDS;
            logger.info(commitMessage);

            long startClass = System.currentTimeMillis();
            git.processClass();
            long endClass = System.currentTimeMillis();
            String classMessage = this.threadIdentity + CollectLogger.ELAPSED_TIME + ((endClass - startClass) / Math.pow(10, 6)) + CollectLogger.SECONDS;
            logger.info(classMessage);
            git.closeRepo();

            long startMetrics = System.currentTimeMillis();
            Metrics metrics = new Metrics(git);



        }
    }
}
