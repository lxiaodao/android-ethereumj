package org.ethereum.db;

import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.Genesis;
import org.ethereum.datasource.mapdb.MapDBFactory;
import org.ethereum.datasource.mapdb.MapDBFactoryImpl;
import org.ethereum.util.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.*;

/**
 * @author Mikhail Kalinin
 * @since 09.07.2015
 */
public class BlockQueueTest {

    private static final Logger logger = LoggerFactory.getLogger("test");

    private BlockQueue blockQueue;
    private List<Block> blocks = new ArrayList<>();
    private String testDb;

    @Before
    public void setup() throws InstantiationException, IllegalAccessException, URISyntaxException, IOException {
        URL scenario1 = ClassLoader
                .getSystemResource("blockstore/load.dmp");

        File file = new File(scenario1.toURI());
        List<String> strData = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

        Block genesis = Genesis.getInstance();
        blocks.add(genesis);

        for (String blockRLP : strData) {
            Block block = new Block(
                    Hex.decode(blockRLP)
            );

            if (block.getNumber() % 1000 == 0)
                logger.info("adding block.hash: [{}] block.number: [{}]",
                        block.getShortHash(),
                        block.getNumber());

            blocks.add(block);
        }

        logger.info("total blocks loaded: {}", blocks.size());

        BigInteger bi = new BigInteger(32, new Random());
        testDb = "test_db_" + bi;
        SystemProperties.CONFIG.setDataBaseDir(testDb);

        MapDBFactory mapDBFactory = new MapDBFactoryImpl();
        blockQueue = new BlockQueueImpl();
        ((BlockQueueImpl)blockQueue).setMapDBFactory(mapDBFactory);
        blockQueue.open();
    }

    @After
    public void cleanup() {
        blockQueue.close();
        FileUtil.recursiveDelete(testDb);
    }

    @Test // basic checks
    public void test1() {
        blockQueue.add(blocks.get(0));

        // testing: peek()
        Block block = blockQueue.peek();

        assertNotNull(block);

        // testing: validity of loaded block
        assertArrayEquals(blocks.get(0).getEncoded(), block.getEncoded());

        blockQueue.poll();

        // testing: addAll(), close(), open()
        blockQueue.addAll(blocks);

        blockQueue.close();
        blockQueue.open();

        assertEquals(blocks.size(), blockQueue.size());

        // testing: poll()
        long prevNumber = -1;
        for(int i = 0; i < blocks.size(); i++) {
            block = blockQueue.poll();
            assertTrue(block.getNumber() > prevNumber);
            prevNumber = block.getNumber();
        }

        assertNull(blockQueue.peek());
        assertNull(blockQueue.poll());
        assertTrue(blockQueue.isEmpty());

        // testing: add()
        for(Block b : blocks) {
            blockQueue.add(b);
        }

        prevNumber = -1;
        for(int i = 0; i < blocks.size(); i++) {
            block = blockQueue.poll();
            assertTrue(block.getNumber() > prevNumber);
            prevNumber = block.getNumber();
        }
    }

    @Test // concurrency
    public void test2() throws InterruptedException {
        new Thread(new Writer(1)).start();
        new Thread(new Reader(1)).start();
        Thread r2 = new Thread(new Reader(2));
        r2.start();
        r2.join();
    }

    private class Reader implements Runnable {

        private int index;

        public Reader(int index) {
            this.index = index;
        }

        @Override
        public void run() {
            try {
                int nullsCount = 0;
                while (nullsCount < 10) {
                    Block b = blockQueue.poll();
                    logger.info("reader {}: {}", index, b == null ? null : b.getShortHash());
                    if(b == null) {
                        nullsCount++;
                    } else {
                        nullsCount = 0;
                    }
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }
        }
    }

    private class Writer implements Runnable {

        private int index;

        public Writer(int index) {
            this.index = index;
        }

        @Override
        public void run() {
            try {
                for(int i = 0; i < 50; i++) {
                    Block b = blocks.get(i);
                    blockQueue.add(b);
                    logger.info("writer {}: {}", index, b.getShortHash());
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }
        }
    }
}
