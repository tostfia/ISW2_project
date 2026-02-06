/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.bookkeeper.proto;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.zookeeper.KeeperException;

import org.apache.bookkeeper.bookie.Bookie;
import org.apache.bookkeeper.bookie.BookieException;
import org.apache.bookkeeper.bookie.ExitCode;
import org.apache.bookkeeper.conf.ServerConfiguration;
import org.apache.bookkeeper.jmx.BKMBeanRegistry;
import org.apache.bookkeeper.proto.NIOServerFactory.Cnxn;
import org.apache.bookkeeper.replication.AutoRecoveryMain;
import org.apache.bookkeeper.replication.ReplicationException.CompatibilityException;
import org.apache.bookkeeper.replication.ReplicationException.UnavailableException;
import org.apache.bookkeeper.util.MathUtils;

import com.google.common.annotations.VisibleForTesting;

import static org.apache.bookkeeper.proto.BookieProtocol.PacketHeader;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.binary.Hex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the server-side part of the BookKeeper protocol.
 *
 */
public class BookieServer implements NIOServerFactory.PacketProcessor, BookkeeperInternalCallbacks.WriteCallback {
    final ServerConfiguration conf;
    NIOServerFactory nioServerFactory;
    private volatile boolean running = false;
    Bookie bookie;
    DeathWatcher deathWatcher;
    static Logger LOG = LoggerFactory.getLogger(BookieServer.class);

    int exitCode = ExitCode.OK;

    // operation stats
    final BKStats bkStats = BKStats.getInstance();
    final boolean isStatsEnabled;
    protected BookieServerBean jmxBkServerBean;
    private AutoRecoveryMain autoRecoveryMain = null;
    private boolean isAutoRecoveryDaemonEnabled;

    public BookieServer(ServerConfiguration conf) throws IOException,
            KeeperException, InterruptedException, BookieException,
            UnavailableException, CompatibilityException {
        this.conf = conf;
        this.bookie = newBookie(conf);
        isAutoRecoveryDaemonEnabled = conf.isAutoRecoveryDaemonEnabled();
        if (isAutoRecoveryDaemonEnabled) {
            this.autoRecoveryMain = new AutoRecoveryMain(conf);
        }
        isStatsEnabled = conf.isStatisticsEnabled();
    }

    protected Bookie newBookie(ServerConfiguration conf)
        throws IOException, KeeperException, InterruptedException, BookieException {
        return new Bookie(conf);
    }

    public void start() throws IOException, UnavailableException {
        nioServerFactory = new NIOServerFactory(conf, this);

        this.bookie.start();
        if (isAutoRecoveryDaemonEnabled && this.autoRecoveryMain != null) {
            this.autoRecoveryMain.start();
        }

        nioServerFactory.start();
        running = true;
        deathWatcher = new DeathWatcher(conf);
        deathWatcher.start();

        // register jmx
        registerJMX();
    }

    public InetSocketAddress getLocalAddress() {
        try {
            return Bookie.getBookieAddress(conf);
        } catch (UnknownHostException uhe) {
            return nioServerFactory.getLocalAddress();
        }
    }

    @VisibleForTesting
    public Bookie getBookie() {
        return bookie;
    }

    /**
     * Suspend processing of requests in the bookie (for testing)
     */
    @VisibleForTesting
    public void suspendProcessing() {
        nioServerFactory.suspendProcessing();
    }

    /**
     * Resume processing requests in the bookie (for testing)
     */
    @VisibleForTesting
    public void resumeProcessing() {
        nioServerFactory.resumeProcessing();
    }

    public synchronized void shutdown() {
        if (!running) {
            return;
        }
        nioServerFactory.shutdown();
        exitCode = bookie.shutdown();
        if (isAutoRecoveryDaemonEnabled && this.autoRecoveryMain != null) {
            this.autoRecoveryMain.shutdown();
        }
        running = false;

        // unregister JMX
        unregisterJMX();
    }

    protected void registerJMX() {
        try {
            jmxBkServerBean = new BookieServerBean(conf, this);
            BKMBeanRegistry.getInstance().register(jmxBkServerBean, null);

            bookie.registerJMX(jmxBkServerBean);
        } catch (Exception e) {
            LOG.warn("Failed to register with JMX", e);
            jmxBkServerBean = null;
        }
    }

    protected void unregisterJMX() {
        try {
            bookie.unregisterJMX();
            if (jmxBkServerBean != null) {
                BKMBeanRegistry.getInstance().unregister(jmxBkServerBean);
            }
        } catch (Exception e) {
            LOG.warn("Failed to unregister with JMX", e);
        }
        jmxBkServerBean = null;
    }

    public boolean isRunning() {
        return bookie.isRunning() && nioServerFactory.isRunning() && running;
    }

    /**
     * Whether bookie is running?
     *
     * @return true if bookie is running, otherwise return false
     */
    public boolean isBookieRunning() {
        return bookie.isRunning();
    }

    /**
     * Whether auto-recovery service running with Bookie?
     *
     * @return true if auto-recovery service is running, otherwise return false
     */
    public boolean isAutoRecoveryRunning() {
        return this.autoRecoveryMain != null
                && this.autoRecoveryMain.isAutoRecoveryRunning();
    }

    /**
     * Whether nio server is running?
     *
     * @return true if nio server is running, otherwise return false
     */
    public boolean isNioServerRunning() {
        return nioServerFactory.isRunning();
    }

    public void join() throws InterruptedException {
        nioServerFactory.join();
    }

    public int getExitCode() {
        return exitCode;
    }

    /**
     * A thread to watch whether bookie & nioserver is still alive
     */
    class DeathWatcher extends Thread {

        final int watchInterval;

        DeathWatcher(ServerConfiguration conf) {
            super("BookieDeathWatcher-" + conf.getBookiePort());
            watchInterval = conf.getDeathWatchInterval();
        }

        @Override
        public void run() {
            while(true) {
                try {
                    Thread.sleep(watchInterval);
                } catch (InterruptedException ie) {
                    // do nothing
                }
                if (!isBookieRunning()
                        || !isNioServerRunning()
                        || (isAutoRecoveryDaemonEnabled && !isAutoRecoveryRunning())) {
                    shutdown();
                    break;
                }
            }
        }
    }

    static final Options bkOpts = new Options();
    static {
        bkOpts.addOption("c", "conf", true, "Configuration for Bookie Server");
        bkOpts.addOption("withAutoRecovery", false,
                "Start Autorecovery service Bookie server");
        bkOpts.addOption("h", "help", false, "Print help message");
    }

    /**
     * Print usage
     */
    private static void printUsage() {
        HelpFormatter hf = new HelpFormatter();
        hf.printHelp("BookieServer [options]\n\tor\n"
                   + "BookieServer <bookie_port> <zk_servers> <journal_dir> <ledger_dir [ledger_dir]>", bkOpts);
    }

    private static void loadConfFile(ServerConfiguration conf, String confFile)
        throws IllegalArgumentException {
        try {
            conf.loadConf(new File(confFile).toURI().toURL());
        } catch (MalformedURLException e) {
            LOG.error("Could not open configuration file: " + confFile, e);
            throw new IllegalArgumentException();
        } catch (ConfigurationException e) {
            LOG.error("Malformed configuration file: " + confFile, e);
            throw new IllegalArgumentException();
        }
        LOG.info("Using configuration file " + confFile);
    }

    private static ServerConfiguration parseArgs(String[] args)
        throws IllegalArgumentException {
        try {
            BasicParser parser = new BasicParser();
            CommandLine cmdLine = parser.parse(bkOpts, args);

            if (cmdLine.hasOption('h')) {
                throw new IllegalArgumentException();
            }

            ServerConfiguration conf = new ServerConfiguration();
            String[] leftArgs = cmdLine.getArgs();

            if (cmdLine.hasOption('c')) {
                if (null != leftArgs && leftArgs.length > 0) {
                    throw new IllegalArgumentException();
                }
                String confFile = cmdLine.getOptionValue("c");
                loadConfFile(conf, confFile);
                return conf;
            }

            if (cmdLine.hasOption("withAutoRecovery")) {
                conf.setAutoRecoveryDaemonEnabled(true);
            }

            if (leftArgs.length < 4) {
                throw new IllegalArgumentException();
            }

            // command line arguments overwrite settings in configuration file
            conf.setBookiePort(Integer.parseInt(leftArgs[0]));
            conf.setZkServers(leftArgs[1]);
            conf.setJournalDirName(leftArgs[2]);
            String[] ledgerDirNames = new String[leftArgs.length - 3];
            System.arraycopy(leftArgs, 3, ledgerDirNames, 0, ledgerDirNames.length);
            conf.setLedgerDirNames(ledgerDirNames);

            return conf;
        } catch (ParseException e) {
            LOG.error("Error parsing command line arguments : ", e);
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * @param args
     * @throws IOException
     * @throws InterruptedException
     */
    public static void main(String[] args) {
        ServerConfiguration conf = null;
        try {
            conf = parseArgs(args);
        } catch (IllegalArgumentException iae) {
            LOG.error("Error parsing command line arguments : ", iae);
            System.err.println(iae.getMessage());
            printUsage();
            System.exit(ExitCode.INVALID_CONF);
        }

        StringBuilder sb = new StringBuilder();
        String[] ledgerDirNames = conf.getLedgerDirNames();
        for (int i = 0; i < ledgerDirNames.length; i++) {
            if (i != 0) {
                sb.append(',');
            }
            sb.append(ledgerDirNames[i]);
        }

        String hello = String.format(
                           "Hello, I'm your bookie, listening on port %1$s. ZKServers are on %2$s. Journals are in %3$s. Ledgers are stored in %4$s.",
                           conf.getBookiePort(), conf.getZkServers(),
                           conf.getJournalDirName(), sb);
        LOG.info(hello);
        try {
            final BookieServer bs = new BookieServer(conf);
            bs.start();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    bs.shutdown();
                    LOG.info("Shut down bookie server successfully");
                }
            });
            LOG.info("Register shutdown hook successfully");
            bs.join();

            System.exit(bs.getExitCode());
        } catch (Exception e) {
            LOG.error("Exception running bookie server : ", e);
            System.exit(ExitCode.SERVER_EXCEPTION);
        }
    }

    //INIZIO

    // Definizione di un handler per opcode
    @FunctionalInterface
    private interface PacketHandler {
        void handle(ByteBuffer packet, PacketHeader header, long ledgerId, long entryId, byte[] masterKey, Cnxn src);
    }

    // Mappa opcode -> handler
    private final Map<Integer, PacketHandler> packetHandlers = new HashMap<>();

    public void initHandlers() {
        packetHandlers.put((int) BookieProtocol.ADDENTRY, this::handleAddEntry);
        packetHandlers.put((int) BookieProtocol.READENTRY, this::handleReadEntry);
    }

    public void processPacket(ByteBuffer packet, Cnxn src) {
        PacketHeader header = PacketHeader.fromInt(packet.getInt());

        if (header.getVersion() < BookieProtocol.LOWEST_COMPAT_PROTOCOL_VERSION
                || header.getVersion() > BookieProtocol.CURRENT_PROTOCOL_VERSION) {
            LOG.error("Invalid protocol version {}, expected between {} & {}");
            src.sendResponse(buildResponse(BookieProtocol.EBADVERSION, header.getVersion(), header.getOpCode(), -1, BookieProtocol.INVALID_ENTRY_ID));
            return;
        }

        long ledgerId = -1;
        long entryId = BookieProtocol.INVALID_ENTRY_ID;
        byte[] masterKey = null;

        // Leggi dati in base all'opcode
        if (header.getOpCode() == BookieProtocol.ADDENTRY) {
            masterKey = new byte[BookieProtocol.MASTER_KEY_LENGTH];
            packet.get(masterKey);
            ByteBuffer bb = packet.duplicate();
            ledgerId = bb.getLong();
            entryId = bb.getLong();
        } else if (header.getOpCode() == BookieProtocol.READENTRY) {
            ledgerId = packet.getLong();
            entryId = packet.getLong();
        }

        // Trova e invoca handler
        PacketHandler handler = packetHandlers.get(header.getOpCode());
        if (handler != null) {
            handler.handle(packet, header, ledgerId, entryId, masterKey, src);
        } else {
            src.sendResponse(buildResponse(BookieProtocol.EBADREQ, header.getVersion(), header.getOpCode(), ledgerId, entryId));
        }
    }

    // Gestione AddEntry
    private void handleAddEntry(ByteBuffer packet, PacketHeader header, long ledgerId, long entryId, byte[] masterKey, Cnxn src) {
        if (bookie.isReadOnly()) {
            LOG.warn("BookieServer is readonly, rejecting request!");
            src.sendResponse(buildResponse(BookieProtocol.EREADONLY, header.getVersion(), header.getOpCode(), ledgerId, entryId));
            return;
        }
        try {
            TimedCnxn tsrc = new TimedCnxn(src, MathUtils.now());
            if ((header.getFlags() & BookieProtocol.FLAG_RECOVERY_ADD) == BookieProtocol.FLAG_RECOVERY_ADD) {
                bookie.recoveryAddEntry(packet.slice(), this, tsrc, masterKey);
            } else {
                bookie.addEntry(packet.slice(), this, tsrc, masterKey);
            }
            updateStats(BKStats.STATS_ADD, true);
        } catch (IOException e) {
            LOG.error("Error writing entry {}@{}");
            src.sendResponse(buildResponse(BookieProtocol.EIO, header.getVersion(), header.getOpCode(), ledgerId, entryId));
            updateStats(BKStats.STATS_ADD, false);
        } catch (BookieException e) {
            handleBookieException(e, header, ledgerId, entryId, src);
            updateStats(BKStats.STATS_ADD, false);
        }
    }

    // Gestione ReadEntry
    private void handleReadEntry(ByteBuffer packet, PacketHeader header, long ledgerId, long entryId, byte[] masterKey, Cnxn src) {
        ByteBuffer[] rsp = new ByteBuffer[2];
        int errorCode = BookieProtocol.EIO;
        try {
            rsp[1] = bookie.readEntry(ledgerId, entryId);
            errorCode = BookieProtocol.EOK;
        } catch (Bookie.NoLedgerException e) {
            errorCode = BookieProtocol.ENOLEDGER;
        } catch (IOException e) {
            errorCode = BookieProtocol.ENOENTRY;

        }

        rsp[0] = buildResponse(errorCode, header.getVersion(), header.getOpCode(), ledgerId, entryId);

        if (rsp[1] == null) {
            rsp[1] = ByteBuffer.allocate(16);
            rsp[1].putLong(ledgerId);
            rsp[1].putLong(entryId);
            rsp[1].flip();
        }
        src.sendResponse(rsp);

        updateStats(BKStats.STATS_READ, errorCode == BookieProtocol.EOK);
    }

    // Aggiorna statistiche
    private void updateStats(int statType, boolean success) {
        if (!isStatsEnabled) return;
        if (success) {
            bkStats.getOpStats(statType).updateLatency(MathUtils.now()); // o calcola differenza se vuoi
        } else {
            bkStats.getOpStats(statType).incrementFailedOps();
        }
    }

    // Gestione eccezioni Bookie
    private void handleBookieException(BookieException e, PacketHeader header, long ledgerId, long entryId, Cnxn src) {
        if (e instanceof BookieException.LedgerFencedException) {
            LOG.error("Attempt to write to fenced ledger", e);
            src.sendResponse(buildResponse(BookieProtocol.EFENCED, header.getVersion(), header.getOpCode(), ledgerId, entryId));
        } else {
            LOG.error("Unauthorized access to ledger {}", ledgerId, e);
            src.sendResponse(buildResponse(BookieProtocol.EUA, header.getVersion(), header.getOpCode(), ledgerId, entryId));
        }
    }



    //FINE

    private ByteBuffer buildResponse(int errorCode, byte version, byte opCode, long ledgerId, long entryId) {
        ByteBuffer rsp = ByteBuffer.allocate(24);
        rsp.putInt(new PacketHeader(version, 
                                    opCode, (short)0).toInt());
        rsp.putInt(errorCode);
        rsp.putLong(ledgerId);
        rsp.putLong(entryId);

        rsp.flip();
        return rsp;
    }

    public void writeComplete(int rc, long ledgerId, long entryId, InetSocketAddress addr, Object ctx) {
        TimedCnxn tcnxn = (TimedCnxn) ctx;
        Cnxn src = tcnxn.cnxn;
        long startTime = tcnxn.time;
        ByteBuffer bb = ByteBuffer.allocate(24);
        bb.putInt(new PacketHeader(BookieProtocol.CURRENT_PROTOCOL_VERSION, 
                                   BookieProtocol.ADDENTRY, (short)0).toInt());
        bb.putInt(rc);
        bb.putLong(ledgerId);
        bb.putLong(entryId);
        bb.flip();
        if (LOG.isTraceEnabled()) {
            LOG.trace("Add entry rc = " + rc + " for " + entryId + "@" + ledgerId);
        }
        src.sendResponse(new ByteBuffer[] { bb });
        if (isStatsEnabled) {
            // compute the latency
            if (0 == rc) {
                // for add operations, we compute latency in writeComplete callbacks.
                long elapsedTime = MathUtils.now() - startTime;
                bkStats.getOpStats(BKStats.STATS_ADD).updateLatency(elapsedTime);
            } else {
                bkStats.getOpStats(BKStats.STATS_ADD).incrementFailedOps();                
            }
        }
    }

    /**
     * A cnxn wrapper for time
     */
    static class TimedCnxn {
        Cnxn cnxn;
        long time;

        public TimedCnxn(Cnxn cnxn, long startTime) {
            this.cnxn = cnxn;
            this.time = startTime;
        }
    }

}
