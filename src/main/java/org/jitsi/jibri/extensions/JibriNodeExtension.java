package org.jitsi.jibri.extensions;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;

/**
 * @Author dzh
 * @Create 2023/8/23 10:06
 * @Package org.jitsi.jicofo.extensions
 * @Description: Copyright (c) Company:Genew Technologies Co., Ltd. 2005-2020
 */
public class JibriNodeExtension implements ExtensionElement {

    public static final String NAMESPACE = "jabber:client";
    public static final String ELEMENT = "jibri_extend";

    private String ip;

    private String status;

    private String name;

    private String id;

    public JibriNodeExtension() {

    }

    public JibriNodeExtension(String ip, String status, String name, String id) {
        this.ip = ip;
        this.status = status;
        this.name = name;
        this.id = id;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String getElementName() {
        return ELEMENT;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }



    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public CharSequence toXML(XmlEnvironment xmlEnvironment) {
        XmlStringBuilder xml = new XmlStringBuilder(this);
        xml.optAttribute("ip", this.getIp());
        xml.optAttribute("name", this.getName());
        xml.optAttribute("status", this.getStatus());
        xml.optAttribute("id", this.getId());
        xml.closeEmptyElement();
        return xml;
    }

    public static class Provider extends ExtensionElementProvider<JibriNodeExtension> {

        public Provider() {
        }


        @Override
        public JibriNodeExtension parse(XmlPullParser xmlPullParser, int i, XmlEnvironment xmlEnvironment) throws XmlPullParserException, IOException, SmackParsingException {
            String ip = xmlPullParser.getAttributeValue("ip");
            String name = xmlPullParser.getAttributeValue("name");
            String status = xmlPullParser.getAttributeValue("status");
            String id = xmlPullParser.getAttributeValue("id");

            while (xmlPullParser.getEventType() != XmlPullParser.Event.END_ELEMENT) {
                xmlPullParser.next();
            }

            return new JibriNodeExtension(ip, status, name, id);
        }
    }

}
