# Salesforce EI Inbound Endpoint

The Salesforce streaming Inbound Endpoint allows you to perform various operations on Salesforce streaming data via WSO2 ESB.

1. The Salesforce streaming API receives notifications based on the changes that happen to Salesforce data with respect to an SQQL (Salesforce Object Query Language) query you define, in a secured and scalable way. For more information, go to [Salesforce streaming documentation](https://developer.salesforce.com/docs/atlas.en-us.202.0.api_streaming.meta/api_streaming/quick_start_workbench.htm).

2. [Platform events](#platform-event)



```
Reliable message delivery is only available from the Salesforce API version 37.0 and above.
```

## Compatibility

| Inbound version  | Supported Salesforce API version | Supported WSO2 ESB/EI version |
| ------------- | ------------- | ------------- |
| 2.0.1| 22.0 | EI 6.1.0, 6.2.0, 6.3.0, 6.4.0, 6.5.0 |
| 2.0.0| 22.0 | EI 6.1.0, 6.2.0, 6.3.0, 6.4.0 |
| 1.0.0| 22.0  | ESB 5.00, EI 6.1.0, 6.1.1, 6.2.0, 6.3.0, 6.4.0 |


## How to use
1. Either [download the inbound endpoit JAR file](https://store.wso2.com/store/assets/esbconnector/details/fbb433b5-4d74-4064-84c2-e4b23c531aa2) or build the project by executing the **mvn clean install** command to get the jar and by adding it in the <ESB-Home>/dropin directory.

2. Create a Sequence to print the logs as follows.
```
<?xml version="1.0" encoding="UTF-8"?>
<sequence name="test" onError="fault" xmlns="http://ws.apache.org/ns/synapse">
    <log level="full"/>
    <drop/>
</sequence>
```
3. Create a Push Topic and then retrieve information or configure a Platform Event.

## Building From the Source

Follow the steps given below to build the Salesforce EI Inbound Endpoint from the source code:

1. Get a clone or download the source from [Github](https://github.com/wso2-extensions/esb-inbound-salesforce).
2. Run the following Maven command from the `esb-inbound-salesforce` directory: `mvn clean install`.
3. The Salesforce inbound endpoint jar file is created in the `esb-inbound-salesforce/target` directory

## Creating a Push Topic 
First, [create a custom object in Salesforce](https://developer.salesforce.com/docs/atlas.en-us.202.0.api_streaming.meta/api_streaming/create_object.htm) and then [create a Push Topic](https://developer.salesforce.com/docs/atlas.en-us.202.0.api_streaming.meta/api_streaming/create_a_pushtopic.htm) that contains an SOQL query.
Next, go to the Developer Console of your Salesforce account and click **Debug->Open Execute Anonymous Window.**. Then, add the following entry in the **Enter Apex Code window**. 

```
PushTopic pushTopic = new PushTopic();
pushTopic.Name = 'InvoiceStatementUpdates';
pushTopic.Query = 'SELECT Id, Name, wso2__Status__c, wso2__Description__c FROM InvoiceStatement__c';
pushTopic.ApiVersion = 37.0;
pushTopic.NotifyForOperationCreate = true;
pushTopic.NotifyForOperationUpdate = true;
pushTopic.NotifyForOperationUndelete = true;
pushTopic.NotifyForOperationDelete = true;
pushTopic.NotifyForFields = 'Referenced';
insert pushTopic;
```
Click **Execute**.


## Retrieving account information 
WSO2 ESB Salesforce Inbound Endpoint acts as a message consumer. It creates a connection to the Salesforce account, consumes the Salesforce data and injects the data to the ESB Sequence.
Now, that you have configured the Salesforce streaming Inbound Endpoint, use the following ESB Inbound Endpoint configuration to retrieve account details from your Salesforce account.

```
<?xml version="1.0" encoding="UTF-8"?>
<inboundEndpoint xmlns="http://ws.apache.org/ns/synapse"
                 name="SaleforceInboundEP"
                 sequence="test"
                 onError="fault"
                 class="org.wso2.carbon.inbound.salesforce.poll.SalesforceStreamData"
                 suspend="false">
   <parameters>
      <parameter name="inbound.behavior">polling</parameter>
      <parameter name="interval">100</parameter>
      <parameter name="sequential">true</parameter>
      <parameter name="coordination">true</parameter>
      <parameter name="connection.salesforce.replay">false</parameter>
      <parameter name="connection.salesforce.EventIDStoredFilePath">/Users/nalaka/Desktop/a.txt</parameter>
      <parameter name="connection.salesforce.packageVersion">37.0</parameter>
      <parameter name="connection.salesforce.salesforceObject">/topic/InvoiceStatementUpdates</parameter>
      <parameter name="connection.salesforce.loginEndpoint">https://login.salesforce.com</parameter>
      <parameter name="connection.salesforce.userName">MyUsername</parameter>
      <parameter name="connection.salesforce.password">MyPassword</parameter>
      <parameter name="connection.salesforce.waitTime">5000</parameter>
      <parameter name="connection.salesforce.connectionTimeout">20000</parameter>
      <parameter name="connection.salesforce.soapApiVersion">22.0</parameter>
   </parameters>
</inboundEndpoint>
```
* connection.salesforce.replay: replay **enable** or **disable**. Enabling this will read the event ID stored in the Registry DB or from the text file stored in the local machine.
* connection.salesforce.EventIDStoredFilePath:
    - when replay is enabled, do not define any value for this property (i.e., keep it blank), to replay from the last event ID stored in the config Registry DB (property- name of the salesforce object(Follow bellow example) resource path:connector/salesforce/event).
    - when replay is enabled, specify the directory path of a text file to start replaying from the event ID stored in it. 
    ```
    Ex: Salesforce object is /topic/reading__e then property name is "reading__e".
    In case of a failure, the default value will be used to retrieve IDs from current events. Create the property for each platform event and each pushtopic in the config Registry DB in the connector/salesforce/event resource path.
    ```
* connection.salesforce.packageVersion: The version of the Salesforce API.
* connection.salesforce.salesforceObject : The name of the Push Topic or the Platform Event that is added to the Salesforce account.
* connection.salesforce.loginEndpoint: The Endpoint of the Salesforce account.
* connection.salesforce.userName:  The username for accessing the Salesforce account.
* connection.salesforce.password: The password provided here is a concatenation of the user password and the security token provided by Salesforce. For more information, see[information on creating a security token in Salesforce].(https://help.salesforce.com/articleView?id=user_security_token.htm&type=5).
* connection.salesforce.waitTime: The time to wait to connect to the Salesforce account(default : 5 * 1000 ms).
* connection.salesforce.connectionTimeout: The time to wait to connect to the client(default : 20 * 1000 ms).
* connection.salesforce.soapApiVersion: The version of the Salesforce SOAP API.

## Platform Event
[Define a Platform Event](https://developer.salesforce.com/docs/atlas.en-us.platform_events.meta/platform_events/platform_events_intro.htm) in the same way you define a custom object in Salesforce.


In the ESB configuration, specify the Event to which you need to subscribe.
```
<parameter name="connection.salesforce.salesforceObject">/event/InvoiceStatementReading1s__e</parameter>
```
Go to the Developer Console of your Salesforce account and click **Debug->Open Execute Anonymous Window.** Add the following entry in the **Enter Apex Code window.** [For instructions, go to Publish Event Messages with Apex.](https://developer.salesforce.com/docs/atlas.en-us.platform_events.meta/platform_events/platform_events_publish_apex.htm).

```
InvoiceStatementReading1s__e event = new InvoiceStatementReading1s__e(Status__c='nnn24');

// Publish event.
Database.SaveResult sr = EventBus.publish(event);

// Inspect publishing result for each event
if (sr.isSuccess()) {
    System.debug('Successfully published event.');
} else {
    for(Database.Error err : sr.getErrors()) {
        System.debug('Error returned: ' +
                    err.getStatusCode() +
                    ' - ' +
                    err.getMessage());
    }
}
```
Click **Execute**.
The Event will be triggered in the ESB in real time.

```
To subscribe to multiple platform events or push topics add the separate inbound endpoit with corresponding sequences for push topics and platform events. Make sure to create resource in the config registry DB to for each object to store event id when replay option need to use.
```

## How to contribute

  * As an open source project, WSO2 extensions welcome contributions from the community.Check the [issue tracker](https://github.com/wso2-extensions/esb-inbound-salesforce/issues) for open issues that interest you. We look forward to receiving your contributions.
  
