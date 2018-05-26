package org.ethereum.db;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.facade.Repository;
import org.ethereum.json.EtherObjectMapper;
import org.ethereum.json.JSONHelper;
import org.ethereum.trie.SecureTrie;
import org.ethereum.trie.Trie;
import org.ethereum.trie.TrieImpl;
import org.ethereum.util.Functional;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nonnull;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Thread.sleep;
import static org.ethereum.config.SystemProperties.CONFIG;
import static org.ethereum.crypto.SHA3Helper.sha3;
import static org.ethereum.util.ByteUtil.wrap;

/**
 * @author Roman Mandeleil
 * @since 17.11.2014
 */
public class RepositoryImpl implements Repository {

    public final static String DETAILS_DB = "details";
    public final static String STATE_DB = "state";

    private static final Logger logger = LoggerFactory.getLogger("repository");
    private static final Logger gLogger = LoggerFactory.getLogger("general");

    private Trie worldState;

    private DatabaseImpl detailsDB = null;
    private DetailsDataStore dds = new DetailsDataStore();

    private DatabaseImpl stateDB = null;

    private KeyValueDataSource detailsDS = null;
    private KeyValueDataSource stateDS = null;

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicInteger accessCounter = new AtomicInteger();


    public RepositoryImpl() {
        this(DETAILS_DB, STATE_DB);
    }

    public RepositoryImpl(boolean createDb) {
    }

    public RepositoryImpl(KeyValueDataSource detailsDS, KeyValueDataSource stateDS) {

        detailsDS.setName(DETAILS_DB);
        detailsDS.init();
        this.detailsDS = detailsDS;

        stateDS.setName(STATE_DB);
        stateDS.init();
        this.stateDS = stateDS;

        detailsDB = new DatabaseImpl(detailsDS);
        dds.setDB(detailsDB);

        stateDB = new DatabaseImpl(stateDS);
        worldState = new SecureTrie(stateDB.getDb());
    }

    public RepositoryImpl(String detailsDbName, String stateDbName) {
        detailsDB = new DatabaseImpl(detailsDbName);
        dds.setDB(detailsDB);

        stateDB = new DatabaseImpl(stateDbName);
        worldState = new SecureTrie(stateDB.getDb());
    }


    @Override
    public void reset() {
        doWithLockedAccess(new Functional.InvokeWrapper() {
            @Override
            public void invoke() {
                close();

                detailsDS.init();
                detailsDB = new DatabaseImpl(detailsDS);

                stateDS.init();
                stateDB = new DatabaseImpl(stateDS);
                worldState = new SecureTrie(stateDB.getDb());
            }
        });
    }
    
    @Override
    public void close() {
        doWithLockedAccess(new Functional.InvokeWrapper() {
            @Override
            public void invoke() {
                if (detailsDB != null) {
                    detailsDB.close();
                    detailsDB = null;
                }
                if (stateDB != null) {
                    stateDB.close();
                    stateDB = null;
                }
            }
        });
    }

    @Override
    public boolean isClosed() {
        return stateDB == null;
    }

    @Override
    public void updateBatch(HashMap<ByteArrayWrapper, AccountState> stateCache,
                            HashMap<ByteArrayWrapper, ContractDetails> detailsCache) {

        logger.info("updatingBatch: detailsCache.size: {}", detailsCache.size());

        for (ByteArrayWrapper hash : stateCache.keySet()) {

            AccountState accountState = stateCache.get(hash);
            ContractDetails contractDetails = detailsCache.get(hash);

            if (accountState.isDeleted()) {
                delete(hash.getData());
                logger.debug("delete: [{}]",
                        Hex.toHexString(hash.getData()));

            } else {

                if (!contractDetails.isDirty()) continue;

                ContractDetailsCacheImpl contractDetailsCache = (ContractDetailsCacheImpl) contractDetails;
                if (contractDetailsCache.origContract == null) {
                    contractDetailsCache.origContract = new ContractDetailsImpl();
                    contractDetailsCache.origContract.setAddress(hash.getData());
                    contractDetailsCache.commit();
                }

                contractDetails = contractDetailsCache.origContract;

                byte[] data = hash.getData();
                updateContractDetails(data, contractDetails);

                accountState.setStateRoot(contractDetails.getStorageHash());
                accountState.setCodeHash(sha3(contractDetails.getCode()));
                updateAccountState(hash.getData(), accountState);

                if (logger.isDebugEnabled()) {
                    logger.debug("update: [{}],nonce: [{}] balance: [{}] \n [{}]",
                            Hex.toHexString(hash.getData()),
                            accountState.getNonce(),
                            accountState.getBalance(),
                            contractDetails.getStorage());
                }
            }
        }


        logger.info("updated: detailsCache.size: {}", detailsCache.size());

        stateCache.clear();
        detailsCache.clear();
    }

    private void updateContractDetails(final byte[] address, final ContractDetails contractDetails) {
        doWithAccessCounting(new Functional.InvokeWrapper() {
            @Override
            public void invoke() {
                dds.update(address, contractDetails);
            }
        });
    }

    @Override
    public void flushNoReconnect() {
        doWithLockedAccess(new Functional.InvokeWrapper() {
            @Override
            public void invoke() {
                gLogger.info("flushing to disk");

                dds.flush();
                worldState.sync();
            }
        });
    }


    @Override
    public void flush() {
        doWithLockedAccess(new Functional.InvokeWrapper() {
            @Override
            public void invoke() {
                gLogger.info("flushing to disk");

                dds.flush();
                worldState.sync();

                byte[] root = worldState.getRootHash();
                reset();
                worldState.setRoot(root);
            }
        });
    }

    public int getAllocatedMemorySize() {
        return doWithAccessCounting(new Functional.InvokeWrapperWithResult<Integer>() {
            @Override
            public Integer invoke() {
                return dds.getAllocatedMemorySize() + ((TrieImpl) worldState).getCache().getAllocatedMemorySize();
            }
        });
    }

    @Override
    public void rollback() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void commit() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void syncToRoot(final byte[] root) {
        doWithAccessCounting(new Functional.InvokeWrapper() {
            @Override
            public void invoke() {
                worldState.setRoot(root);
            }
        });
    }

    @Override
    public Repository startTracking() {
        return new RepositoryTrack(this);
    }

    @Override
    public void dumpState(Block block, long gasUsed, int txNumber, byte[] txHash) {
        dumpTrie(block);

        if (!(CONFIG.dumpFull() || CONFIG.dumpBlock() == block.getNumber()))
            return;

        // todo: dump block header and the relevant tx

        if (block.getNumber() == 0 && txNumber == 0)
            if (CONFIG.dumpCleanOnRestart()) {
                try{
                	FileUtils.forceDelete(new File(CONFIG.dumpDir()));
                }catch(IOException e){
                	logger.error(e.getMessage(), e);
                }
            }

        String dir = CONFIG.dumpDir() + "/";

        String fileName = "";
        if (txHash != null)
            fileName = String.format("%07d_%d_%s.dmp", block.getNumber(), txNumber,
                    Hex.toHexString(txHash).substring(0, 8));
        else {
            fileName = String.format("%07d_c.dmp", block.getNumber());
        }

        File dumpFile = new File(System.getProperty("user.dir") + "/" + dir + fileName);
        FileWriter fw = null;
        BufferedWriter bw = null;
        try {

            dumpFile.getParentFile().mkdirs();
            dumpFile.createNewFile();

            fw = new FileWriter(dumpFile.getAbsoluteFile());
            bw = new BufferedWriter(fw);

            List<ByteArrayWrapper> keys = this.detailsDB.dumpKeys();

            JsonNodeFactory jsonFactory = new JsonNodeFactory(false);
            ObjectNode blockNode = jsonFactory.objectNode();

            JSONHelper.dumpBlock(blockNode, block, gasUsed,
                    this.getRoot(),
                    keys, this);

            EtherObjectMapper mapper = new EtherObjectMapper();
            bw.write(mapper.writeValueAsString(blockNode));

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                if (bw != null) bw.close();
                if (fw != null) fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String getTrieDump() {
        return doWithAccessCounting(new Functional.InvokeWrapperWithResult<String>() {
            @Override
            public String invoke() {
                return worldState.getTrieDump();
            }
        });
    }

    public void dumpTrie(Block block) {

        if (!(CONFIG.dumpFull() || CONFIG.dumpBlock() == block.getNumber()))
            return;

        String fileName = String.format("%07d_trie.dmp", block.getNumber());
        String dir = CONFIG.dumpDir() + "/";
        File dumpFile = new File(System.getProperty("user.dir") + "/" + dir + fileName);
        FileWriter fw = null;
        BufferedWriter bw = null;

        String dump = getTrieDump();

        try {

            dumpFile.getParentFile().mkdirs();
            dumpFile.createNewFile();

            fw = new FileWriter(dumpFile.getAbsoluteFile());
            bw = new BufferedWriter(fw);

            bw.write(dump);

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                if (bw != null) bw.close();
                if (fw != null) fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public Set<byte[]> getAccountsKeys() {
        return doWithAccessCounting(new Functional.InvokeWrapperWithResult<Set<byte[]>>() {
            @Override
            public Set<byte[]> invoke() {
                Set<byte[]> result = new HashSet<>();
                for (ByteArrayWrapper key : dds.keys()) {
                    result.add(key.getData());
                }

                return result;
            }
        });
    }

    @Override
    public BigInteger addBalance(byte[] addr, BigInteger value) {

        AccountState account = getAccountStateOrCreateNew(addr);

        BigInteger result = account.addToBalance(value);
        updateAccountState(addr, account);

        return result;
    }

    @Override
    public BigInteger getBalance(byte[] addr) {
        AccountState account = getAccountState(addr);
        return (account == null) ? BigInteger.ZERO : account.getBalance();
    }

    @Override
    public DataWord getStorageValue(byte[] addr, DataWord key) {
        ContractDetails details = getContractDetails(addr);
        return (details == null) ? null : details.get(key);
    }

    @Override
    public void addStorageRow(byte[] addr, DataWord key, DataWord value) {
        ContractDetails details = getContractDetails(addr);
        if (details == null) {
            createAccount(addr);
            details = getContractDetails(addr);
        }

        details.put(key, value);

        updateContractDetails(addr, details);
    }

    @Override
    public byte[] getCode(byte[] addr) {
        ContractDetails details = getContractDetails(addr);
        return (details == null) ? null : details.getCode();
    }

    @Override
    public void saveCode(byte[] addr, byte[] code) {
        ContractDetails details = getContractDetails(addr);

        if (details == null) {
            createAccount(addr);
            details = getContractDetails(addr);
        }

        details.setCode(code);

        updateContractDetails(addr, details);
    }


    @Override
    public BigInteger getNonce(byte[] addr) {
        return getAccountStateOrCreateNew(addr).getNonce();
    }

    @Nonnull
    private AccountState getAccountStateOrCreateNew(byte[] addr) {
        AccountState account = getAccountState(addr);
        return (account == null) ? createAccount(addr) : account;
    }

    @Override
    public BigInteger increaseNonce(byte[] addr) {
        AccountState account = getAccountStateOrCreateNew(addr);

        account.incrementNonce();
        updateAccountState(addr, account);

        return account.getNonce();
    }

    private void updateAccountState(final byte[] addr, final AccountState accountState) {
        doWithAccessCounting(new Functional.InvokeWrapper() {
            @Override
            public void invoke() {
                worldState.update(addr, accountState.getEncoded());
            }
        });
    }

    public BigInteger setNonce(final byte[] addr, final BigInteger nonce) {
        AccountState account = getAccountStateOrCreateNew(addr);

        account.setNonce(nonce);
        updateAccountState(addr, account);

        return account.getNonce();
    }

    @Override
    public void delete(final byte[] addr) {
        doWithAccessCounting(new Functional.InvokeWrapper() {
            @Override
            public void invoke() {
                worldState.delete(addr);
                dds.remove(addr);
            }
        });
    }

    @Override
    public ContractDetails getContractDetails(final byte[] addr) {
        return doWithAccessCounting(new Functional.InvokeWrapperWithResult<ContractDetails>() {
            @Override
            public ContractDetails invoke() {
                return dds.get(addr);
            }
        });
    }

    @Override
    public AccountState getAccountState(final byte[] addr) {
        return doWithAccessCounting(new Functional.InvokeWrapperWithResult<AccountState>() {
            @Override
            public AccountState invoke() {
                AccountState result = null;
                byte[] accountData = worldState.get(addr);

                if (accountData.length != 0)
                    result = new AccountState(accountData);

                return result;
            }
        });
    }

    @Override
    public AccountState createAccount(final byte[] addr) {
        AccountState accountState = new AccountState();

        updateAccountState(addr, accountState);
        updateContractDetails(addr, new ContractDetailsImpl());

        return accountState;
    }

    @Override
    public boolean isExist(byte[] addr) {
        return getAccountState(addr) != null;
    }

    @Override
    public void loadAccount(byte[] addr,
                            HashMap<ByteArrayWrapper, AccountState> cacheAccounts,
                            HashMap<ByteArrayWrapper, ContractDetails> cacheDetails) {

        AccountState account = getAccountState(addr);
        ContractDetails details = getContractDetails(addr);

        account = (account == null) ? new AccountState() : account.clone();
        details = new ContractDetailsCacheImpl(details);
//        details.setAddress(addr);

        ByteArrayWrapper wrappedAddress = wrap(addr);
        cacheAccounts.put(wrappedAddress, account);
        cacheDetails.put(wrappedAddress, details);
    }

    @Override
    public byte[] getRoot() {
        return worldState.getRootHash();
    }

    private void doWithLockedAccess(Functional.InvokeWrapper wrapper) {
        lock.lock();
        try {
            while (accessCounter.get() > 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("waiting for access ...");
                }
                try {
                    sleep(100);
                } catch (InterruptedException e) {
                    logger.error("Error occurred during access waiting: ", e);
                }
            }

            wrapper.invoke();
        } finally {
            lock.unlock();
        }
    }

    public <R> R doWithAccessCounting(Functional.InvokeWrapperWithResult<R> wrapper) {
        while (lock.isLocked()) {
            if (logger.isDebugEnabled()) {
                logger.debug("waiting for lock releasing ...");
            }
            try {
                sleep(100);
            } catch (InterruptedException e) {
                logger.error("Error occurred during locked access waiting: ", e);
            }
        }
        accessCounter.incrementAndGet();
        try {
            return wrapper.invoke();
        } finally {
            accessCounter.decrementAndGet();
        }
    }

    public void doWithAccessCounting(final Functional.InvokeWrapper wrapper) {
        doWithAccessCounting(new Functional.InvokeWrapperWithResult<Object>() {
            @Override
            public Object invoke() {
                wrapper.invoke();
                return null;
            }
        });
    }
}
