package org.ethereum.net.server;

import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.listener.EthereumListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author Roman Mandeleil
 * @since 11.11.2014
 */
@Singleton
public class ChannelManager {

    private static final Logger logger = LoggerFactory.getLogger("net");

    Timer inactivesCollector = new Timer("inactivesCollector");
    List<Channel> channels = Collections.synchronizedList(new ArrayList<Channel>());

    Map<ByteArrayWrapper, Block> blockCache = new HashMap<>();

    EthereumListener listener;

    @Inject
    public ChannelManager(EthereumListener listener) {
        this.listener = listener;

        this.init();
    }

    public void init() {
        scheduleChannelCollector();
    }


    public Channel getChannel(String peerId) {

        for (Channel channel : channels) {
            String tablePeerId = channel.getP2pHandler().getHandshakeHelloMessage().getPeerId();
            if (tablePeerId != null &&
                    peerId.equals(tablePeerId)) {
                return channel;
            }
        }
        return null;
    }

    public void recvTransaction() {
        // ???
    }


    public void recvBlock() { // todo:
        // 1. Check in the cache if the hash exist
        // 2. Exist: go and send it to the queue
    }

    public void sendTransaction(Transaction tx) {
        for (Channel channel : channels) {
            channel.sendTransaction(tx);
        }
    }


    public void sendNewBlock(Block block) {
        // 1. Go over all channels and send the block
        for (Channel channel : channels) {
            channel.sendNewBlock(block);
        }
    }

    public void addChannel(Channel channel) {
        synchronized (channels) {
            channels.add(channel);
        }
    }

    public boolean isAllSync() {

        boolean result = true;
        for (Channel channel : channels) {
            result &= channel.isSync();
        }

        return result;
    }

    public void scheduleChannelCollector() {
        inactivesCollector.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                Iterator<Channel> iter = channels.iterator();
                while (iter.hasNext()) {
                    Channel channel = iter.next();

                    // todo: identify how to remove channels
//                        iter.remove();
//                        logger.info("Channel removed: {}", channel.p2pHandler.getHandshakeHelloMessage());
                }

                if (channels.size() == 0) {
                    listener.onNoConnections();
                }
            }
        }, 2000, 5000);
    }

    public void reconnect() {
        for (Channel channel : channels)
            channel.p2pHandler.sendDisconnect();
    }

    public void ethSync() {

        Channel bestChannel = channels.get(0);
        for (Channel channel : channels) {

            if (bestChannel.getTotalDifficulty().
                    compareTo(channel.getTotalDifficulty()) < 0) {
                bestChannel = channel;
            }
        }
        bestChannel.ethSync();
    }

    public List<Channel> getChannels() {
        return channels;
    }
}
