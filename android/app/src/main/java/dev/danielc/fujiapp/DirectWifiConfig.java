package dev.danielc.fujiapp;

/** Validation shared with the direct connection form. */
final class DirectWifiConfig {
    static boolean validAddress(String address) {
        String[] octets=address.split("\\.",-1);if(octets.length!=4)return false;
        for(String octet:octets){if(!octet.matches("[0-9]{1,3}"))return false;int value=Integer.parseInt(octet);if(value>255)return false;}
        return !address.equals("0.0.0.0")&&!address.equals("255.255.255.255");
    }
}
