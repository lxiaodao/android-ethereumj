package org.ethereum.core;


import org.ethereum.config.SystemProperties;
import org.ethereum.db.BlockStore;
import org.ethereum.db.InMemoryBlockStore;
import org.ethereum.di.components.TestEthereumComponent;
import org.ethereum.di.modules.TestEthereumModule;
import org.ethereum.di.components.DaggerTestEthereumComponent;
import org.ethereum.facade.Ethereum;
import org.ethereum.manager.WorldManager;
import org.hibernate.SessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.ethereum.TestContext;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import javax.inject.Inject;

import static org.junit.Assert.assertEquals;

public class ImportTest {

    private static final Logger logger = LoggerFactory.getLogger("test");

    static class ContextConfiguration extends TestContext {
        static {
            SystemProperties.CONFIG.setDataBaseDir("test_db/" + ImportTest.class);
            SystemProperties.CONFIG.setDatabaseReset(true);
        }

        public BlockStore blockStore(SessionFactory sessionFactory){
            return new InMemoryBlockStore();
        }
    }

    @Inject
    WorldManager worldManager;

    @Inject
    public ImportTest() {

    }

    @Before
    public void setup() {
        TestEthereumComponent component = DaggerTestEthereumComponent.builder()
                .testEthereumModule(new TestEthereumModule())
                .build();
        //Ethereum ethereum = component.ethereum();
        worldManager = component.worldManager();
        // TODO: load blockchain, otherwise bestblock error occurs ??
        worldManager.loadBlockchain();
    }

    @After
    public void close(){
        worldManager.close();
    }


    @Test
    public void testScenario1() throws URISyntaxException, IOException {

        logger.error("Started");

        BlockchainImpl blockchain = (BlockchainImpl) worldManager.getBlockchain();

        URL scenario1 = ClassLoader
                .getSystemResource("blockload/scenario1.dmp");

        File file = new File(scenario1.toURI());
        List<String> strData = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

        byte[] root = Genesis.getInstance().getStateRoot();
        for (String blockRLP : strData) {
            Block block = new Block(
                    Hex.decode(blockRLP));
            logger.info("sending block.hash: {}", Hex.toHexString(block.getHash()));
            blockchain.tryToConnect(block);
            root = block.getStateRoot();
        }

        logger.info("asserting root state is: {}", Hex.toHexString(root));
        assertEquals(Hex.toHexString(root),
                Hex.toHexString(worldManager.getRepository().getRoot()));
    }

}
