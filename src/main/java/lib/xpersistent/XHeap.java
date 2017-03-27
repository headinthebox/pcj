/* Copyright (C) 2017  Intel Corporation
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 only, as published by the Free Software Foundation.
 * This file has been designated as subject to the "Classpath"
 * exception as provided in the LICENSE file that accompanied
 * this code.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License version 2 for more details (a copy
 * is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU General Public License
 * version 2 along with this program; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

package lib.xpersistent;

import lib.util.persistent.PersistentHeap;
import lib.util.persistent.MemoryRegion;
import lib.util.persistent.PersistenceException;
import lib.util.persistent.spi.PersistentMemoryProvider;
import lib.util.persistent.Root;
import lib.util.persistent.ObjectPointer;
import lib.util.persistent.PersistentObject;
import lib.util.persistent.PersistentLong;
import lib.util.persistent.CycleCollector;
import lib.util.persistent.Transaction;
import lib.util.persistent.ObjectDirectory;
import java.util.concurrent.ConcurrentHashMap;

public class XHeap implements PersistentHeap {
    static {
        System.loadLibrary("Persistent");
    }

    private final PersistentMemoryProvider provider;
    private Root root;
    private boolean open;
    private boolean debug;
    private ConcurrentHashMap<Long, MemoryRegion> regions;

    public XHeap() {
        this(PersistentMemoryProvider.getDefaultProvider());
    }

    public XHeap(PersistentMemoryProvider provider) {
        this.provider = provider;
        this.debug = false;
        regions = new ConcurrentHashMap<>();
        //open();
    }

    public synchronized void open() {
        if (open) return;
        this.open = true;
        nativeOpenHeap();
        cleanHeap();
    }

    public synchronized void close() {
        if (!open) throw new PersistenceException("Heap not open!");
        this.open = false;
        nativeCloseHeap();
    }

    public synchronized MemoryRegion allocateRegion(long size) {
        if (!open) open();
        long addr = nativeGetMemoryRegion(size);
        MemoryRegion reg = new UncheckedPersistentMemoryRegion(addr);
        regions.put(reg.addr(), reg);
        return reg;
    }

    public synchronized MemoryRegion regionFromAddress(long addr) {
        if (!open) open();
        MemoryRegion reg;
        if ((reg = regions.get(addr)) == null) {
            reg = new UncheckedPersistentMemoryRegion(addr);
            regions.put(addr, reg);
        }
        return reg;
    }

    public synchronized void freeRegion(MemoryRegion region) {
        if (!open) open();
        regions.remove(region.addr());
        nativeFree(region.addr());
    }

    public synchronized Root getRoot() {
        if (this.root == null) {
            initRoot();
            ObjectDirectory.initialize();
        }
        return root;
    }

    public synchronized void initRoot() {
        if (!open) open();
        if (this.root == null) this.root = new XRoot(this);
    }

    public void memcpy(MemoryRegion srcRegion, long srcOffset, MemoryRegion destRegion, long destOffset, long length) {
        nativeMemoryRegionMemcpy(srcRegion.addr(), srcOffset, destRegion.addr(), destOffset, length);
    }

    public void setDebugMode(boolean debug) {
        this.debug = debug;
    }
    public boolean getDebugMode() { return this.debug; }

    public void debug() {
        ((XRoot)root).printAllObjects();
        nativeDebugPool();
    }

    private void cleanHeap() {
        Transaction.run(() -> {
            XRoot rt = (XRoot)(getRoot());
            CycleCollector.getCandidates().addAll(rt.importCandidates());
            rt.cleanVMOffsets();
            //CycleCollector.collect();
        });
    }

    private synchronized native void nativeOpenHeap();
    private synchronized native void nativeCloseHeap();
    private synchronized native long nativeGetMemoryRegion(long size);
    private synchronized native void nativeFree(long addr);
    private synchronized native void nativeMemoryRegionMemcpy(long srcRegion, long srcOffset, long destRegion, long destOffset, long length);
    private synchronized native void nativeDebugPool();
}
