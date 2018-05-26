package org.ethereum.net;

import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.facade.Blockchain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

import javax.inject.Inject;
import javax.inject.Singleton;

import static java.lang.Thread.sleep;
import static org.ethereum.config.SystemProperties.CONFIG;
import static org.ethereum.core.ImportResult.NO_PARENT;
import static org.ethereum.core.ImportResult.SUCCESS;

/**
 * The processing queue for blocks to be validated and added to the blockchain.
 * This class also maintains the list of hashes from the peer with the heaviest sub-tree.
 * Based on these hashes, blocks are added to the queue.
 *
 * @author Roman Mandeleil
 * @since 27.07.2014
 */
@Singleton
public class BlockQueue {

    private static final Logger logger = LoggerFactory.getLogger("blockqueue");

    /**
     * The list of hashes of the heaviest chain on the network,
     * for which this client doesn't have the blocks yet
     */
    private Deque<byte[]> blockHashQueue = new ArrayDeque<>();

    /**
     * Queue with blocks to be validated and added to the blockchain
     */
    private PriorityBlockingQueue<Block> blockReceivedQueue = new PriorityBlockingQueue<>(1000, new BlockByNumberComparator());

    /**
     * Highest known total difficulty, representing the heaviest chain on the network
     */
    private BigInteger highestTotalDifficulty;

    /**
     * Last block in the queue to be processed
     */
    private Block lastBlock;

    private Timer timer = new Timer("BlockQueueTimer");

    Blockchain blockchain;

    @Inject
    public BlockQueue(Blockchain blockchain) {

        this.blockchain = blockchain;
        Runnable queueProducer = new Runnable(){

            @Override
            public void run() {
                produceQueue();
            }
        };

        Thread t=new Thread (queueProducer);
        t.start();
    }

    /**
     * Processing the queue adding blocks to the chain.
     */
    private void produceQueue() {

        while (1==1){

            try {
                Block block = blockReceivedQueue.take();
                logger.info("BlockQueue size: {}", blockReceivedQueue.size());
                ImportResult importResult = blockchain.tryToConnect(block);

                // In case we don't have a parent on the chain
                // return the try and wait for more blocks to come.
                if (importResult == NO_PARENT){
                    logger.info("No parent on the chain for block.number: [{}]", block.getNumber());
                    blockReceivedQueue.add(block);
                    sleep(2000);
                }


                if (importResult == SUCCESS)
                    logger.info("Success importing: block number: {}", block.getNumber());

            } catch (Throwable e) {
                logger.error("Error: {} ", e);
            }

        }
    }

    /**
     * Add a list of blocks to the processing queue.
     * The list is validated by making sure the first block in the received list of blocks
     * is the next expected block number of the queue.
     *
     * The queue is configured to contain a maximum number of blocks to avoid memory issues
     * If the list exceeds that, the rest of the received blocks in the list are discarded.
     *
     * @param blockList - the blocks received from a peer to be added to the queue
     */
    public void addBlocks(List<Block> blockList) {

        for (Block block : blockList)
            blockReceivedQueue.put(block);

        lastBlock = blockList.get(blockList.size() - 1);

        logger.info("Blocks waiting to be proceed:  queue.size: [{}] lastBlock.number: [{}]",
                blockReceivedQueue.size(),
                lastBlock.getNumber());
    }

    /**
     * adding single block to the queue usually
     * a result of a NEW_BLOCK message announce.
     *
     * @param block - new block
     */
    public void addBlock(Block block) {

        blockReceivedQueue.add(block);
        lastBlock = block;

        logger.debug("Blocks waiting to be proceed:  queue.size: [{}] lastBlock.number: [{}]",
                blockReceivedQueue.size(),
                lastBlock.getNumber());
    }

    /**
     * Returns the last block in the queue. If the queue is empty,
     * this will return the last block added to the blockchain.
     *
     * @return The last known block this client on the network
     * and will never return <code>null</code> as there is
     * always the Genesis block at the start of the chain.
     */
    public Block getLastBlock() {
        if (blockReceivedQueue.isEmpty())
            return blockchain.getBestBlock();
        return lastBlock;
    }

    /**
     * Reset the queue of hashes of blocks to be retrieved
     * and add the best hash to the top of the queue
     *
     * @param hash - the best hash
     */
    public void setBestHash(byte[] hash) {
        blockHashQueue.clear();
        blockHashQueue.addLast(hash);
    }

    /**
     * Returns the last added hash to the queue representing
     * the latest known block on the network
     *
     * @return The best hash on the network known to the client
     */
    public byte[] getBestHash() {
        return blockHashQueue.peekLast();
    }

    public void addHash(byte[] hash) {
        blockHashQueue.addLast(hash);

        if (logger.isTraceEnabled()) {
            logger.trace("Adding hash to a hashQueue: [{}], hash queue size: {} ",
                    Hex.toHexString(hash).substring(0, 6),
                    blockHashQueue.size());
        }
    }

    public void returnHashes(List<ByteArrayWrapper> hashes) {

        if (hashes.isEmpty()) return;

        logger.info("Hashes remained uncovered: hashes.size: [{}]", hashes.size());

        ListIterator iterator = hashes.listIterator(hashes.size());
        while (iterator.hasPrevious()) {

            byte[] hash = ((ByteArrayWrapper) iterator.previous()).getData();

            if (logger.isDebugEnabled())
                logger.debug("Return hash: [{}]", Hex.toHexString(hash));
            blockHashQueue.addLast(hash);
        }
    }

    public void addNewBlockHash(byte[] hash) {
        blockHashQueue.addFirst(hash);
    }

    /**
     * Return a list of hashes from blocks that still need to be downloaded.
     *
     * @return A list of hashes for which blocks need to be retrieved.
     */
    public List<byte[]> getHashes() {

        List<byte[]> hashes = new ArrayList<>();
        while (!blockHashQueue.isEmpty() && hashes.size() < CONFIG.maxBlocksAsk()) {
            hashes.add(blockHashQueue.removeLast());
        }
        return hashes;
    }

    // a bit ugly but really gives
    // good result
    public void logHashQueueSize() {
        logger.info("Block hashes list size: [{}]", blockHashQueue.size());
    }

    private class BlockByNumberComparator implements Comparator<Block> {

        @Override
        public int compare(Block o1, Block o2) {

            if (o1 == null || o2 == null)
                throw new NullPointerException();

            if (o1.getNumber() > o2.getNumber())
                return 1;
            if (o1.getNumber() < o2.getNumber())
                return -1;

            return 0;
        }
    }

    public BigInteger getHighestTotalDifficulty() {
        return highestTotalDifficulty;
    }

    public void setHighestTotalDifficulty(BigInteger highestTotalDifficulty) {
        this.highestTotalDifficulty = highestTotalDifficulty;
    }

    /**
     * Returns the current number of blocks in the queue
     *
     * @return the current number of blocks in the queue
     */
    public int size() {
        return blockReceivedQueue.size();
    }

    public boolean isHashesEmpty() {
        return blockHashQueue.size() == 0;
    }

    public void clear() {
        this.blockHashQueue.clear();
        this.blockReceivedQueue.clear();
    }

    /**
     * Cancel and purge the timer-thread that
     * processes the blocks in the queue
     */
    public void close() {
        timer.cancel();
        timer.purge();
    }


}