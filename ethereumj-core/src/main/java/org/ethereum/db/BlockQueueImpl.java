package org.ethereum.db;

import org.ethereum.core.Block;
import org.ethereum.datasource.mapdb.MapDBFactory;

import java.util.*;

/**
 * @author Mikhail Kalinin
 * @since 09.07.2015
 */
public class BlockQueueImpl implements BlockQueue {

    private MapDBFactory mapDBFactory;

    private Map<Long, Block> blocks;
    private List<Long> index;

    @Override
    public void open() {
        blocks = mapDBFactory.createBlockQueueMap();
        index = new ArrayList<>(blocks.keySet());
        sortIndex();
    }

    @Override
    public void close() {
        mapDBFactory.destroy(blocks);
    }

    @Override
    public synchronized void addAll(Collection<Block> blockList) {
        List<Long> numbers = new ArrayList<>(blockList.size());
        for(Block b : blockList) {
            blocks.put(b.getNumber(), b);
            numbers.add(b.getNumber());
        }
        index.addAll(numbers);
        sortIndex();
    }

    @Override
    public synchronized void add(Block block) {
        blocks.put(block.getNumber(), block);
        index.add(block.getNumber());
        sortIndex();
    }

    @Override
    public synchronized Block poll() {
        if(!index.isEmpty()) {
            Long idx = index.get(0);
            Block block = blocks.get(idx);
            blocks.remove(idx);
            index.remove(0);
            return block;
        } else {
            return null;
        }
    }

    @Override
    public synchronized Block peek() {
        if(!index.isEmpty()) {
            Long idx = index.get(0);
            return blocks.get(idx);
        } else {
            return null;
        }
    }

    @Override
    public int size() {
        return index.size();
    }

    @Override
    public boolean isEmpty() {
        return index.isEmpty();
    }

    private void sortIndex() {
        Collections.sort(index);
    }

    public void setMapDBFactory(MapDBFactory mapDBFactory) {
        this.mapDBFactory = mapDBFactory;
    }
}
