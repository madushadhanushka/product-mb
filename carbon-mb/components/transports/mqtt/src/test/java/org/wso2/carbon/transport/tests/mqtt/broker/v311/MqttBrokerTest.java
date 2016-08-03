/*
 * Copyright (c) 2016, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.transport.tests.mqtt.broker.v311;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.carbon.andes.transports.mqtt.MqttConstants;
import org.wso2.carbon.andes.transports.mqtt.adaptors.common.QOSLevel;
import org.wso2.carbon.andes.transports.mqtt.broker.MqttChannel;
import org.wso2.carbon.andes.transports.mqtt.broker.v311.MqttBroker;
import org.wso2.carbon.andes.transports.mqtt.netty.protocol.messages.AbstractMessage;
import org.wso2.carbon.andes.transports.mqtt.netty.protocol.messages.ConnAckMessage;
import org.wso2.carbon.andes.transports.mqtt.netty.protocol.messages.DisconnectMessage;
import org.wso2.carbon.andes.transports.mqtt.netty.protocol.messages.PublishMessage;
import org.wso2.carbon.andes.transports.mqtt.netty.protocol.messages.SubAckMessage;
import org.wso2.carbon.andes.transports.mqtt.netty.protocol.messages.SubscribeMessage;
import org.wso2.carbon.andes.transports.mqtt.netty.protocol.messages.UnsubAckMessage;
import org.wso2.carbon.andes.transports.mqtt.netty.protocol.messages.UnsubscribeMessage;
import org.wso2.carbon.andes.transports.server.BrokerException;
import org.wso2.carbon.kernel.runtime.exception.RuntimeServiceException;
import org.wso2.carbon.transport.tests.mqtt.broker.v311.adaptors.MockMemoryAdaptor;
import org.wso2.carbon.transport.tests.mqtt.broker.v311.client.ClientMessageReceiver;
import org.wso2.carbon.transport.tests.mqtt.broker.v311.dataprovider.MqttBrokerDataProvider;
import org.wso2.carbon.transport.tests.mqtt.broker.v311.dataprovider.commands.Message;

import java.util.List;

/**
 * Tests Mqtt broker command messages
 */
public class MqttBrokerTest {

    /**
     * Will create a broker instance and get the relevant response for the command message
     *
     * @param command MQTT command message
     * @return response message which will represent the ack which will be sent to the client
     */
    private AbstractMessage createBrokerAndGetResponse(Message command) throws BrokerException {
        ClientMessageReceiver clientMessageReceiver = new ClientMessageReceiver();
        MqttBroker broker = new MqttBroker();
        broker.connect(command.getMessage(), clientMessageReceiver.getMqttChannel());
        return clientMessageReceiver.getResponseMessage();
    }

    /**
     * Tests MQTT connect message command
     *
     * @param connection the connection information which includes connection flags
     * @throws Exception
     */
    @Test(dataProvider = "ConnectMessage", dataProviderClass = MqttBrokerDataProvider.class)
    public void testConnect(Message connection) throws Exception {
        AbstractMessage responseMessage = createBrokerAndGetResponse(connection);
        byte expectedResult = (byte) connection.getExpectedResult();

        if (responseMessage instanceof ConnAckMessage) {
            byte returnCode = ((ConnAckMessage) responseMessage).getReturnCode();
            Assert.assertEquals(returnCode, expectedResult);
        } else {
            throw new RuntimeServiceException("Error occurred while casting the acknowledgment");
        }
    }

    /**
     * Tests MQTT disconnection message command
     *
     * @param disconnection the disconnection information
     * @throws Exception
     */
    @Test(dataProvider = "DisconnectMessage", dataProviderClass = MqttBrokerDataProvider.class)
    public void testDisconnect(Message disconnection) throws Exception {
        //We need to declare a message adopter, message adopter would be responsible to maintain the state
        MockMemoryAdaptor adopter = new MockMemoryAdaptor();
        ClientMessageReceiver clientMessageReceiver = new ClientMessageReceiver();
        DisconnectMessage disconnectMessage = (DisconnectMessage) disconnection.getMessage();

        //Before testing the disconnection we need to create a connection
        String clientId = disconnection.getMessageProperty("Client-ID");
        String topicFilter = disconnection.getMessageProperty("topicFilter");
        String userName = disconnection.getMessageProperty("userName");
        int qos = Integer.parseInt(disconnection.getMessageProperty("qosLevel"));
        QOSLevel qosLevel = QOSLevel.getQoSFromValue(qos);
        boolean session = Boolean.parseBoolean(disconnection.getMessageProperty("session"));


        MqttChannel mqttChannel = clientMessageReceiver.getMqttChannel();
        adopter.storeSubscriptions(topicFilter, clientId, userName, session, qosLevel, mqttChannel);
        //We need to reflect those subscriptions in the channel
        clientMessageReceiver.getMqttChannel().addTopic(topicFilter, qos);
        clientMessageReceiver.getMqttChannel().addProperty(MqttConstants.CLIENT_ID_PROPERTY_NAME, clientId);


        MqttBroker broker = new MqttBroker();
        broker.disconnect(disconnectMessage, mqttChannel, adopter);

        //We now need to check against the store whether the disconnection is successful
        Assert.assertTrue(!adopter.isSubscriptionLive(topicFilter, clientId));

    }

    /**
     * Test subscription command messages
     *
     * @param subscribe holds the commands releated to the subscription
     * @throws Exception
     */
    @Test(dataProvider = "SubscribeMessage", dataProviderClass = MqttBrokerDataProvider.class)
    public void testSubscribe(Message subscribe) throws Exception {
        MqttBroker broker = new MqttBroker();
        MockMemoryAdaptor adopter = new MockMemoryAdaptor();
        ClientMessageReceiver messageReceiver = new ClientMessageReceiver();
        SubscribeMessage message = (SubscribeMessage) subscribe.getMessage();
        MqttChannel mqttChannel = messageReceiver.getMqttChannel();

        broker.subscribe(message, mqttChannel, adopter);

        SubAckMessage responseMessage = (SubAckMessage) messageReceiver.getResponseMessage();

        List<AbstractMessage.QOSType> types = responseMessage.types();

        //We need to compare whether the expected result is obtained, 1 means expects the subscription to succeed 2
        // means expects the subscription to fail
        int expectedSubscriptionCount = (int) subscribe.getExpectedResult();
        int actualResult = types.size();

        Assert.assertTrue(expectedSubscriptionCount == actualResult);

    }


    /**
     * Test un subscription command message
     *
     * @param unSubscribe holds the un-subscription command message information
     * @throws Exception
     */
    @Test(dataProvider = "unSubscribeMessage", dataProviderClass = MqttBrokerDataProvider.class)
    public void testUnSubscribe(Message unSubscribe) throws Exception {
        MqttBroker broker = new MqttBroker();
        ClientMessageReceiver messageReceiver = new ClientMessageReceiver();
        MockMemoryAdaptor adaptor = new MockMemoryAdaptor();
        UnsubscribeMessage unSubscriptionMessage = (UnsubscribeMessage) unSubscribe.getMessage();
        MqttChannel mqttChannel = messageReceiver.getMqttChannel();
        //Before un-subscribing we need to subscribe first
        //Before testing the disconnection we need to create a connection
        String clientId = unSubscribe.getMessageProperty("Client-ID");
        String topicFilter = unSubscribe.getMessageProperty("topicFilter");
        String userName = unSubscribe.getMessageProperty("userName");
        int qos = Integer.parseInt(unSubscribe.getMessageProperty("qosLevel"));
        QOSLevel qosLevel = QOSLevel.getQoSFromValue(qos);
        boolean session = Boolean.parseBoolean(unSubscribe.getMessageProperty("session"));
        messageReceiver.getMqttChannel().addTopic(topicFilter, qos);
        messageReceiver.getMqttChannel().addProperty(MqttConstants.CLIENT_ID_PROPERTY_NAME, clientId);
        adaptor.storeSubscriptions(topicFilter, clientId, userName, session, qosLevel, mqttChannel);

        broker.unSubscribe(unSubscriptionMessage, mqttChannel, adaptor);

        //We need to check whether the unsubscribe ack was received
        AbstractMessage responseMessage = messageReceiver.getResponseMessage();

        Assert.assertTrue((responseMessage != null && responseMessage instanceof UnsubAckMessage) && !adaptor
                .isSubscriptionLive(topicFilter, clientId));

    }

    /**
     * Test message publish command
     *
     * @param publishMessage the information related to the published message
     * @throws Exception
     */
    @Test(dataProvider = "PublishMessage", dataProviderClass = MqttBrokerDataProvider.class)
    public void testPublish(Message publishMessage) throws Exception {
        MqttBroker broker = new MqttBroker();
        ClientMessageReceiver messageReceiver = new ClientMessageReceiver();
        MockMemoryAdaptor adaptor = new MockMemoryAdaptor();
        PublishMessage message = (PublishMessage) publishMessage.getMessage();
        MqttChannel mqttChannel = messageReceiver.getMqttChannel();

        broker.publish(message, mqttChannel, adaptor);

        if (message.getQos().getValue() > AbstractMessage.QOSType.MOST_ONE.getValue()) {
            //Here we need to validate whether the acknowledgment is sent properly
            AbstractMessage responseMessage = messageReceiver.getResponseMessage();
            AbstractMessage.QOSType qos = responseMessage.getQos();
        }
    }

}