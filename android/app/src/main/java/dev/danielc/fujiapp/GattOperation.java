package dev.danielc.fujiapp;

import java.util.Objects;

/** Identifies the exact callback allowed to finish a serialized GATT request. */
final class GattOperation {
    enum Kind { CONNECT, SERVICES, READ, WRITE, DESCRIPTOR }
    final Kind kind;
    final String service, characteristic, descriptor;
    GattOperation(Kind kind, String service, String characteristic, String descriptor) {
        this.kind=kind; this.service=service; this.characteristic=characteristic; this.descriptor=descriptor;
    }
    boolean matches(GattOperation actual) {
        return actual != null && kind == actual.kind && Objects.equals(service, actual.service)
            && Objects.equals(characteristic, actual.characteristic) && Objects.equals(descriptor, actual.descriptor);
    }
    static GattOperation simple(Kind kind) { return new GattOperation(kind, null, null, null); }
}
