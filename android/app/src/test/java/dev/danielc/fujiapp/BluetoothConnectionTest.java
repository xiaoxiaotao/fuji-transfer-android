package dev.danielc.fujiapp;

import android.bluetooth.*;
import android.content.Context;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class BluetoothConnectionTest {
    @Test public void unnamedSecureAndLegacyAdvertisementsAreRecognized() {
        assertEquals(0x04d8,FujiBleAdvertisement.COMPANY_ID);
        assertTrue(FujiBleAdvertisement.matches(null,new byte[6],Collections.emptyList()));
        assertTrue(FujiBleAdvertisement.matches(null,new byte[5],Collections.emptyList()));
        assertFalse(FujiBleAdvertisement.matches(null,new byte[2],Collections.emptyList()));
    }
    @Test public void secureServiceIsRecognizedWithoutManufacturerOrName() {
        assertTrue(FujiBleAdvertisement.matches(null,null,Collections.singletonList("a9d2b304-e8d6-4902-8336-352b772d7597")));
        assertFalse(FujiBleAdvertisement.matches("Headphones",null,Collections.emptyList()));
    }
    @Test public void callbackMustMatchKindServiceCharacteristicAndDescriptor() {
        GattOperation expected=new GattOperation(GattOperation.Kind.DESCRIPTOR,"service","characteristic","cccd");
        assertTrue(expected.matches(new GattOperation(GattOperation.Kind.DESCRIPTOR,"service","characteristic","cccd")));
        assertFalse(expected.matches(new GattOperation(GattOperation.Kind.WRITE,"service","characteristic",null)));
        assertFalse(expected.matches(new GattOperation(GattOperation.Kind.DESCRIPTOR,"other","characteristic","cccd")));
        assertFalse(expected.matches(new GattOperation(GattOperation.Kind.DESCRIPTOR,"service","other","cccd")));
        assertFalse(expected.matches(new GattOperation(GattOperation.Kind.DESCRIPTOR,"service","characteristic","other")));
    }
    @Test public void lateCallbacksCannotCompleteAnotherOperationOrConnection() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        FujiBluetooth connection=new FujiBluetooth(context,message->{});
        BluetoothGatt active=org.robolectric.shadows.ShadowBluetoothGatt.newInstance(null);
        BluetoothGatt old=org.robolectric.shadows.ShadowBluetoothGatt.newInstance(null);
        Field gattField=FujiBluetooth.class.getDeclaredField("gatt");gattField.setAccessible(true);gattField.set(connection,active);
        Field callbackField=FujiBluetooth.class.getDeclaredField("callback");callbackField.setAccessible(true);
        BluetoothGattCallback callback=(BluetoothGattCallback)callbackField.get(connection);
        BluetoothGattService service=new BluetoothGattService(UUID.randomUUID(),0);
        BluetoothGattCharacteristic target=new BluetoothGattCharacteristic(UUID.randomUUID(),BluetoothGattCharacteristic.PROPERTY_READ|BluetoothGattCharacteristic.PROPERTY_WRITE,0);
        BluetoothGattCharacteristic other=new BluetoothGattCharacteristic(UUID.randomUUID(),BluetoothGattCharacteristic.PROPERTY_READ,0);
        service.addCharacteristic(target);service.addCharacteristic(other);
        Method begin=FujiBluetooth.class.getDeclaredMethod("begin",GattOperation.class);begin.setAccessible(true);
        Field pendingField=FujiBluetooth.class.getDeclaredField("pending");pendingField.setAccessible(true);
        try {
            begin.invoke(connection,new GattOperation(GattOperation.Kind.READ,service.getUuid().toString(),target.getUuid().toString(),null));
            CountDownLatch pending=(CountDownLatch)pendingField.get(connection);
            callback.onCharacteristicWrite(active,target,0);
            callback.onCharacteristicRead(active,other,new byte[]{1},0);
            callback.onCharacteristicRead(old,target,new byte[]{2},0);
            callback.onServicesDiscovered(active,0);
            assertEquals(1,pending.getCount());
            callback.onCharacteristicRead(active,target,new byte[]{3},0);
            assertEquals(0,pending.getCount());
            Field value=FujiBluetooth.class.getDeclaredField("value");value.setAccessible(true);
            callback.onCharacteristicRead(active,target,new byte[]{4},0);
            assertArrayEquals(new byte[]{3},(byte[])value.get(connection));
        } finally {connection.close();old.close();}
    }
    @Test public void disconnectAbortsPendingOperationAndRejectsOldCallbacks() throws Exception {
        Context context=RuntimeEnvironment.getApplication();FujiBluetooth connection=new FujiBluetooth(context,message->{});
        BluetoothGatt active=org.robolectric.shadows.ShadowBluetoothGatt.newInstance(null);
        Field gattField=FujiBluetooth.class.getDeclaredField("gatt");gattField.setAccessible(true);gattField.set(connection,active);
        Method begin=FujiBluetooth.class.getDeclaredMethod("begin",GattOperation.class);begin.setAccessible(true);begin.invoke(connection,GattOperation.simple(GattOperation.Kind.SERVICES));
        Field pendingField=FujiBluetooth.class.getDeclaredField("pending");pendingField.setAccessible(true);
        CountDownLatch pending=(CountDownLatch)pendingField.get(connection);
        try {connection.disconnect();assertEquals(0,pending.getCount());assertNull(gattField.get(connection));}
        finally {connection.close();}
    }
}
