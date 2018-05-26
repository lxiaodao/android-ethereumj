package org.ethereum.db;

import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.HashUtil;
import org.hibernate.SessionFactory;

import java.math.BigInteger;

import java.util.List;

/**
 * @author Roman Mandeleil
 * @since 10.02.2015
 */
public class BlockStoreDummy implements BlockStore {

    @Override
    public byte[] getBlockHashByNumber(long blockNumber) {

        byte[] data = String.valueOf(blockNumber).getBytes();
        return HashUtil.sha3(data);
    }

    @Override
    public Block getBlockByNumber(long blockNumber) {
        return null;
    }

    @Override
    public Block getBlockByHash(byte[] hash) {
        return null;
    }

    @Override
    public List<byte[]> getListHashesEndWith(byte[] hash, long qty) {
        return null;
    }

    @Override
    public void saveBlock(Block block, List<TransactionReceipt> receipts) {
    }

    @Override
    public BigInteger getTotalDifficulty() {
        return null;
    }

    @Override
    public Block getBestBlock() {
        return null;
    }


    @Override
    public void flush() {
    }

    @Override
    public void load() {
    }

    @Override
    public void setSessionFactory(SessionFactory sessionFactory) {
    }

}
