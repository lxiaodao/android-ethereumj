package org.ethereum.vm;

import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.ContractDetails;
import org.ethereum.facade.Repository;
import org.ethereum.vmtrace.ProgramTraceListener;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Storage implements Repository, ProgramTraceListenerAware {

    private final Repository repository;
    private final DataWord address;
    private ProgramTraceListener traceListener;

    public Storage(DataWord address, Repository repository) {
        this.address = address;
        this.repository = repository;
    }

    @Override
    public void setTraceListener(ProgramTraceListener listener) {
        this.traceListener = listener;
    }

    @Override
    public AccountState createAccount(byte[] addr) {
        return repository.createAccount(addr);
    }

    @Override
    public boolean isExist(byte[] addr) {
        return repository.isExist(addr);
    }

    @Override
    public AccountState getAccountState(byte[] addr) {
        return repository.getAccountState(addr);
    }

    @Override
    public void delete(byte[] addr) {
        if (canListenTrace(addr)) traceListener.onStorageClear();
        repository.delete(addr);
    }

    @Override
    public BigInteger increaseNonce(byte[] addr) {
        return repository.increaseNonce(addr);
    }

    @Override
    public BigInteger getNonce(byte[] addr) {
        return repository.getNonce(addr);
    }

    @Override
    public ContractDetails getContractDetails(byte[] addr) {
        return repository.getContractDetails(addr);
    }

    @Override
    public void saveCode(byte[] addr, byte[] code) {
        repository.saveCode(addr, code);
    }

    @Override
    public byte[] getCode(byte[] addr) {
        return repository.getCode(addr);
    }

    @Override
    public void addStorageRow(byte[] addr, DataWord key, DataWord value) {
        if (canListenTrace(addr)) traceListener.onStoragePut(key, value);
        repository.addStorageRow(addr, key, value);
    }

    private boolean canListenTrace(byte[] address) {
        return this.address.equals(new DataWord(address)) && (traceListener != null);
    }

    @Override
    public DataWord getStorageValue(byte[] addr, DataWord key) {
        return repository.getStorageValue(addr, key);
    }

    @Override
    public BigInteger getBalance(byte[] addr) {
        return repository.getBalance(addr);
    }

    @Override
    public BigInteger addBalance(byte[] addr, BigInteger value) {
        return repository.addBalance(addr, value);
    }

    @Override
    public Set<byte[]> getAccountsKeys() {
        return repository.getAccountsKeys();
    }

    @Override
    public void dumpState(Block block, long gasUsed, int txNumber, byte[] txHash) {
        repository.dumpState(block, gasUsed, txNumber, txHash);
    }

    @Override
    public Repository startTracking() {
        return repository.startTracking();
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public void flushNoReconnect() {
        throw new UnsupportedOperationException();
    }


    @Override
    public void commit() {
        repository.commit();
    }

    @Override
    public void rollback() {
        repository.rollback();
    }

    @Override
    public void syncToRoot(byte[] root) {
        repository.syncToRoot(root);
    }

    @Override
    public boolean isClosed() {
        return repository.isClosed();
    }

    @Override
    public void close() {
        repository.close();
    }

    @Override
    public void reset() {
        repository.reset();
    }

    @Override
    public void updateBatch(HashMap<ByteArrayWrapper, AccountState> accountStates, HashMap<ByteArrayWrapper, ContractDetails> contractDetails) {
        for (ByteArrayWrapper address : contractDetails.keySet()) {
            if (!canListenTrace(address.getData())) return;

            ContractDetails details = contractDetails.get(address);
            if (details.isDeleted()) {
                traceListener.onStorageClear();
            } else if (details.isDirty()) {
                for (Map.Entry<DataWord, DataWord> entry : details.getStorage().entrySet()) {
                    traceListener.onStoragePut(entry.getKey(), entry.getValue());
                }
            }
        }
        repository.updateBatch(accountStates, contractDetails);
    }

    @Override
    public byte[] getRoot() {
        return repository.getRoot();
    }

    @Override
    public void loadAccount(byte[] addr, HashMap<ByteArrayWrapper, AccountState> cacheAccounts, HashMap<ByteArrayWrapper, ContractDetails> cacheDetails) {
        repository.loadAccount(addr, cacheAccounts, cacheDetails);
    }
}
