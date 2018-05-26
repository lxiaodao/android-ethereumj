package org.ethereum.db;

import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.util.ByteUtil;
import org.hibernate.SessionFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman Mandeleil
 * @since 12.11.2014
 */
public class BlockStoreImpl implements BlockStore {

    private SessionFactory sessionFactory;

    public BlockStoreImpl(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public byte[] getBlockHashByNumber(long blockNumber) {

        Block block = getBlockByNumber(blockNumber);
        if (block != null) return block.getHash();
        return ByteUtil.EMPTY_BYTE_ARRAY;
    }

    @Override
    public Block getBlockByNumber(long blockNumber) {

        List result = sessionFactory.getCurrentSession().
                createQuery("from BlockVO where number = :number").
                setParameter("number", blockNumber).list();

        if (result.size() == 0) return null;
        BlockVO vo = (BlockVO) result.get(0);

        return new Block(vo.rlp);
    }

    @Override
    public Block getBlockByHash(byte[] hash) {

        List result = sessionFactory.getCurrentSession().
                createQuery("from BlockVO where hash = :hash").
                setParameter("hash", hash).list();

        if (result.size() == 0) return null;
        BlockVO vo = (BlockVO) result.get(0);

        return new Block(vo.rlp);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<byte[]> getListHashesEndWith(byte[] hash, long qty){

        List<byte[]> hashes = new ArrayList<>();

        // find block number of that block hash
        Block block = getBlockByHash(hash);
        if (block == null) return hashes;

        List<byte[]> result = sessionFactory.getCurrentSession().
                createQuery("select hash from BlockVO where number <= :number and number >= :limit order by number desc").
                setParameter("number", block.getNumber()).
                setParameter("limit", block.getNumber() - qty).
                setMaxResults((int)qty).list();

        for (byte[] h : result)
            hashes.add(h);

        return hashes;
    }


    @Override
    public void saveBlock(Block block, List<TransactionReceipt> receipts) {

        BlockVO blockVO = new BlockVO(block.getNumber(), block.getHash(),
                block.getEncoded(), block.getCumulativeDifficulty());

        for (TransactionReceipt receipt : receipts) {

            byte[] hash = receipt.getTransaction().getHash();
            byte[] rlp = receipt.getEncoded();

            TransactionReceiptVO transactionReceiptVO = new TransactionReceiptVO(hash, rlp);
            sessionFactory.getCurrentSession().persist(transactionReceiptVO);
        }

        sessionFactory.getCurrentSession().persist(blockVO);
    }


    @Override
    public BigInteger getTotalDifficulty() {

        return (BigInteger) sessionFactory.getCurrentSession().
                createQuery("select sum(cumulativeDifficulty) from BlockVO").uniqueResult();
    }


    @Override
    public Block getBestBlock() {

        Long bestNumber = (Long)
                sessionFactory.getCurrentSession().createQuery("select max(number) from BlockVO").uniqueResult();
        List result = sessionFactory.getCurrentSession().
                createQuery("from BlockVO where number = :number").setParameter("number", bestNumber).list();

        if (result.isEmpty()) return null;
        BlockVO vo = (BlockVO) result.get(0);

        return new Block(vo.rlp);
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
