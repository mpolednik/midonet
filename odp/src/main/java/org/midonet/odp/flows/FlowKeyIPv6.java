/*
 * Copyright (c) 2012 Midokura SARL, All Rights Reserved.
 */
package org.midonet.odp.flows;

import java.util.Arrays;
import java.nio.ByteBuffer;

import org.midonet.netlink.BytesUtil;
import org.midonet.netlink.NetlinkMessage;
import org.midonet.netlink.messages.Builder;
import org.midonet.packets.IPv6Addr;
import org.midonet.packets.Net;

public class FlowKeyIPv6 implements FlowKey {

    /*__be32*/ private int[] ipv6_src = new int[4];
    /*__be32*/ private int[] ipv6_dst = new int[4];
    /*__be32*/ private int ipv6_label;    /* 20-bits in least-significant bits. */
    /*__u8*/ private byte ipv6_proto;
    /*__u8*/ private byte ipv6_tclass;
    /*__u8*/ private byte ipv6_hlimit;
    /*__u8*/ private byte ipv6_frag;    /* One of OVS_FRAG_TYPE_*. */

    // This is used for deserialization purposes only.
    FlowKeyIPv6() { }

    FlowKeyIPv6(IPv6Addr source, IPv6Addr destination, byte protocol,
                byte hlimit, byte fragmentType) {
        ipv6_src = toIntArray(source);
        ipv6_dst = toIntArray(destination);
        ipv6_proto = protocol;
        ipv6_hlimit = hlimit;
        ipv6_frag = fragmentType;
    }

    public FlowKeyIPv6(int[] source, int[] destination, byte protocol,
                       byte hlimit, byte fragmentType) {
        ipv6_src = source;
        ipv6_dst = destination;
        ipv6_proto = protocol;
        ipv6_hlimit = hlimit;
        ipv6_frag = fragmentType;
    }

    private static int[] toIntArray(IPv6Addr addr) {
        return new int[] {
            (int)(addr.upperWord() >>> 32),
            (int)(addr.upperWord()),
            (int)(addr.lowerWord() >>> 32),
            (int)(addr.lowerWord())
        };
    }

    public int serializeInto(ByteBuffer buffer) {
        BytesUtil.instance.writeBEIntsInto(buffer, ipv6_src);
        BytesUtil.instance.writeBEIntsInto(buffer, ipv6_dst);
        buffer.putInt(BytesUtil.instance.reverseBE(ipv6_label));
        buffer.put(ipv6_proto);
        buffer.put(ipv6_tclass);
        buffer.put(ipv6_hlimit);
        buffer.put(ipv6_frag);
        return 40;
    }

    @Override
    public void serialize(Builder builder) {
        for (int x : ipv6_src) {
            builder.addValue(BytesUtil.instance.reverseBE(x));
        }
        for (int x : ipv6_dst) {
            builder.addValue(BytesUtil.instance.reverseBE(x));
        }
        builder.addValue(BytesUtil.instance.reverseBE(ipv6_label));
        builder.addValue(ipv6_proto);
        builder.addValue(ipv6_tclass);
        builder.addValue(ipv6_hlimit);
        builder.addValue(ipv6_frag);
    }

    @Override
    public boolean deserialize(NetlinkMessage message) {
        try {
            for (int i = 0; i < 4; i++) {
                ipv6_src[i] = BytesUtil.instance.reverseBE(message.getInt());
            }
            for (int i = 0; i < 4; i++) {
                ipv6_dst[i] = BytesUtil.instance.reverseBE(message.getInt());
            }
            ipv6_label = BytesUtil.instance.reverseBE(message.getInt());
            ipv6_proto = message.getByte();
            ipv6_tclass = message.getByte();
            ipv6_hlimit = message.getByte();
            ipv6_frag = message.getByte();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public NetlinkMessage.AttrKey<FlowKeyIPv6> getKey() {
        return FlowKeyAttr.IPv6;
    }

    @Override
    public short attrId() {
        return FlowKeyAttr.IPv6.getId();
    }

    @Override
    public FlowKeyIPv6 getValue() {
        return this;
    }

    public int[] getSrc() {
        return ipv6_src;
    }

    public int[] getDst() {
        return ipv6_dst;
    }

    public int getLabel() {
        return ipv6_label;
    }
    public byte getProto() {
        return ipv6_proto;
    }

    public byte getTClass() {
        return ipv6_tclass;
    }

    public byte getHLimit() {
        return ipv6_hlimit;
    }

    public IPFragmentType getFrag() {
        return IPFragmentType.fromByte(ipv6_frag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        @SuppressWarnings("unchecked")
        FlowKeyIPv6 that = (FlowKeyIPv6) o;

        return (ipv6_frag == that.ipv6_frag)
            && (ipv6_hlimit == that.ipv6_hlimit)
            && (ipv6_label == that.ipv6_label)
            && (ipv6_proto == that.ipv6_proto)
            && (ipv6_tclass == that.ipv6_tclass)
            && Arrays.equals(ipv6_dst, that.ipv6_dst)
            && Arrays.equals(ipv6_src, that.ipv6_src);
    }

    @Override
    public int hashCode() {
        int result = ipv6_src != null ? Arrays.hashCode(ipv6_src) : 0;
        result = 31 * result + (ipv6_dst != null ? Arrays.hashCode(
            ipv6_dst) : 0);
        result = 31 * result + ipv6_label;
        result = 31 * result + (int) ipv6_proto;
        result = 31 * result + (int) ipv6_tclass;
        result = 31 * result + (int) ipv6_hlimit;
        result = 31 * result + (int) ipv6_frag;
        return result;
    }

    @Override
    public String toString() {
        return "FlowKeyIPv6{" +
            "ipv6_src=" + Net.convertIPv6BytesToString(ipv6_src) +
            ", ipv6_dst=" + Net.convertIPv6BytesToString(ipv6_dst) +
            ", ipv6_label=" + ipv6_label +
            ", ipv6_proto=" + ipv6_proto +
            ", ipv6_tclass=" + ipv6_tclass +
            ", ipv6_hlimit=" + ipv6_hlimit +
            ", ipv6_frag=" + ipv6_frag +
            '}';
    }
}
