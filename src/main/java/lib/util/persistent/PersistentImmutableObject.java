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

package lib.util.persistent;

import lib.util.persistent.types.Types;
import lib.util.persistent.types.PersistentType;
import lib.util.persistent.types.ObjectType;
import lib.util.persistent.types.ByteField;
import lib.util.persistent.types.ShortField;
import lib.util.persistent.types.IntField;
import lib.util.persistent.types.LongField;
import lib.util.persistent.types.FloatField;
import lib.util.persistent.types.DoubleField;
import lib.util.persistent.types.CharField;
import lib.util.persistent.types.BooleanField;
import lib.util.persistent.types.ObjectField;
import lib.util.persistent.types.FinalByteField;
import lib.util.persistent.types.FinalShortField;
import lib.util.persistent.types.FinalIntField;
import lib.util.persistent.types.FinalLongField;
import lib.util.persistent.types.FinalFloatField;
import lib.util.persistent.types.FinalDoubleField;
import lib.util.persistent.types.FinalCharField;
import lib.util.persistent.types.FinalBooleanField;
import lib.util.persistent.types.FinalObjectField;
import lib.util.persistent.types.PersistentField;
import java.lang.reflect.Constructor;
import static lib.util.persistent.Trace.*;
import java.util.function.Consumer;
import java.util.Arrays;

public class PersistentImmutableObject extends AnyPersistent {
    boolean[] uninitializedFieldState; 

    PersistentImmutableObject(ObjectType<? extends AnyPersistent> type) {
        this(type, type.isValueBased() ? new VolatileMemoryRegion(type.getAllocationSize()) : heap.allocateRegion(type.getAllocationSize()));
    }

    <T extends AnyPersistent> PersistentImmutableObject(ObjectType<T> type, MemoryRegion region) {
        super(type, region);
    }

    @SuppressWarnings("unchecked")
    protected <T extends PersistentImmutableObject> PersistentImmutableObject(ObjectType<? extends PersistentImmutableObject> type, Consumer<T> initializer) {
        super(type);
        uninitializedFieldState = new boolean[type.fieldCount()];
        Arrays.fill(uninitializedFieldState, true);
        initializer.accept((T)this);
        uninitializedFieldState = null;        
    }

    protected PersistentImmutableObject(ObjectPointer<? extends AnyPersistent> p) {
        super(p);
    }

    @Override
    protected byte getByte(long offset) {
        return getRegionByte(offset);
    }

    @Override
    protected short getShort(long offset) {
        return getRegionShort(offset);
    }

    @Override
    protected int getInt(long offset) {
        return getRegionInt(offset);
    }

    @Override
    protected long getLong(long offset) {
        // trace(true, "PIO getLong(%d)", offset);
        return getRegionLong(offset);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T extends AnyPersistent> T getObject(long offset) {
        return getObject(offset, Types.OBJECT);
    }

    @SuppressWarnings("unchecked")
    <T extends AnyPersistent> T getObject(long offset, PersistentType type) {
        // trace(true, "PIO getObject(%d, %s)", offset, type);
        T ans = null;
        if (type instanceof ObjectType && ((ObjectType)type).isValueBased()) {
            MemoryRegion srcRegion = getPointer().region();
            MemoryRegion dstRegion = new VolatileMemoryRegion(type.getSize());
            // trace(true, "getObject (valueBased) src addr = %d, dst  = %s, size = %d", srcRegion.addr(), dstRegion, type.getSize());
            Util.memCopy(getPointer().type(), (ObjectType)type, srcRegion, offset, dstRegion, 0L, type.getSize());
            T obj = null;
            try {
                Constructor ctor = ((ObjectType)type).cls().getDeclaredConstructor(ObjectPointer.class);
                ctor.setAccessible(true);
                ObjectPointer p = new ObjectPointer<T>((ObjectType)type, dstRegion);
                obj = (T)ctor.newInstance(p);
            }
            catch (Exception e) {e.printStackTrace();}
            ans = obj;
        }
        else {
            long valueAddr = getRegionLong(offset);
            if (valueAddr != 0) ans = (T)ObjectCache.get(valueAddr);
            return ans;
        }
        return ans;
    }

    public byte getByteField(FinalByteField f) {return getRegionByte(offset(check(f.getIndex(), Types.BYTE)));}
    public short getShortField(FinalShortField f) {return getRegionShort(offset(check(f.getIndex(), Types.SHORT)));}
    public int getIntField(FinalIntField f) {return getRegionInt(offset(check(f.getIndex(), Types.INT)));}
    public long getLongField(FinalLongField f) {return getRegionLong(offset(check(f.getIndex(), Types.LONG)));}
    public float getFloatField(FinalFloatField f) {return Float.intBitsToFloat(getRegionInt(offset(check(f.getIndex(), Types.FLOAT))));}
    public double getDoubleField(FinalDoubleField f) {return Double.longBitsToDouble(getRegionLong(offset(check(f.getIndex(), Types.DOUBLE))));}
    public char getCharField(FinalCharField f) {return (char)getRegionInt(offset(check(f.getIndex(), Types.CHAR)));}
    public boolean getBooleanField(FinalBooleanField f) {return getRegionByte(offset(check(f.getIndex(), Types.BOOLEAN))) == 0 ? false : true;}
    
    @SuppressWarnings("unchecked") public <T extends AnyPersistent> T getObjectField(FinalObjectField<T> f) {
        return (T)getObject(offset(f.getIndex()), f.getType());
    }


    void setByteField(ByteField f, byte value) {setByte(offset(check(f.getIndex(), Types.BYTE)), value);}
    void setShortField(ShortField f, short value) {setShort(offset(check(f.getIndex(), Types.SHORT)), value);}
    void setIntField(IntField f, int value) {setInt(offset(check(f.getIndex(), Types.INT)), value);}
    void setLongField(LongField f, long value) {/*System.out.println("PIO setLongField");*/ setLong(offset(check(f.getIndex(), Types.LONG)), value);}
    void setFloatField(FloatField f, float value) {setInt(offset(check(f.getIndex(), Types.FLOAT)), Float.floatToIntBits(value));}
    void setDoubleField(DoubleField f, double value) {setLong(offset(check(f.getIndex(), Types.DOUBLE)), Double.doubleToLongBits(value));}
    void setCharField(CharField f, char value) {setInt(offset(check(f.getIndex(), Types.CHAR)), (int)value);}
    void setBooleanField(BooleanField f, boolean value) {setByte(offset(check(f.getIndex(), Types.BOOLEAN)), value ? (byte)1 : (byte)0);}
    <T extends AnyPersistent> void setObjectField(ObjectField<T> f, T value) {setObjectField(f.getIndex(), value);}

    public void initByteField(FinalByteField f, byte value) {checkUninitializedField(f); setByte(offset(check(f.getIndex(), Types.BYTE)), value);}
    public void initShortField(FinalShortField f, short value) {checkUninitializedField(f); setShort(offset(check(f.getIndex(), Types.SHORT)), value);}
    public void initIntField(FinalIntField f, int value) {checkUninitializedField(f); setInt(offset(check(f.getIndex(), Types.INT)), value);}
    public void initLongField(FinalLongField f, long value) {/*System.out.println("PIO initLongField"); */checkUninitializedField(f); setLong(offset(check(f.getIndex(), Types.LONG)), value);}
    public void initFloatField(FinalFloatField f, float value) {checkUninitializedField(f); setInt(offset(check(f.getIndex(), Types.FLOAT)), Float.floatToIntBits(value));}
    public void initDoubleField(FinalDoubleField f, double value) {checkUninitializedField(f); setLong(offset(check(f.getIndex(), Types.DOUBLE)), Double.doubleToLongBits(value));}
    public void initCharField(FinalCharField f, char value) {checkUninitializedField(f); setInt(offset(check(f.getIndex(), Types.CHAR)), (int)value);}
    public void initBooleanField(FinalBooleanField f, boolean value) {checkUninitializedField(f); setByte(offset(check(f.getIndex(), Types.BOOLEAN)), value ? (byte)1 : (byte)0);}
    public <T extends AnyPersistent> void initObjectField(FinalObjectField<T> f, T value) {checkUninitializedField(f); setObjectField(f.getIndex(), value);}

    private void checkUninitializedField(PersistentField f) {
        if (uninitializedFieldState == null || !uninitializedFieldState[f.getIndex()]) throw new RuntimeException("field already initialized");
    }


}
