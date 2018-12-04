# Configuring Salesforce Inbound Endpoint

The Salesforce streaming inbound endpoint allows you to perform various Salesforce streaming data via WSO2 ESB.

1. The Salesforce streaming API receives notifications for changes to Salesforce data that match a Salesforce Object Query Language (SOQL) query you define, in a secure and scalable way. For more information see [Salesforce streaming documentation](https://developer.salesforce.com/docs/atlas.en-us.202.0.api_streaming.meta/api_streaming/quick_start_workbench.htm) .

2. [Platform events](#platform-event)



```
Reliable message delivery is only available in Salesforce API version 37.0 and later.
```

## How To Use
1. Either [download the inbound endpoit jar](https://store.wso2.com/store/assets/esbconnector/details/fbb433b5-4d74-4064-84c2-e4b23c531aa2) or build the project **mvn clean install** to get the jar and add it in the <ESB-Home>/dropin directory.

2. Create a sequence to print logs as follows.
```
<?xml version="1.0" encoding="UTF-8"?>
<sequence name="test" onError="fault" xmlns="http://ws.apache.org/ns/synapse">
    <log level="full"/>
</sequence>
```
3. Create pushTopic and then retrieve information or configure for platform event.

## Creating a PushTopic 
you need to first [create a custome object in salesforce](https://developer.salesforce.com/docs/atlas.en-us.202.0.api_streaming.meta/api_streaming/create_object.htm) and [create PushTopic](https://developer.salesforce.com/docs/atlas.en-us.202.0.api_streaming.meta/api_streaming/create_a_pushtopic.htm) that contains an SOQL query.
Go to the developer console of your Salesforce account and click on **Debug->Open Execute Anonymous Window.** Add the following entry in the Enter Apex Code window. 

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


## Retrieving the Account Information 
WSO2 ESB Salesforce inbound endpoint acts as a message consumer. It creates a connection to the Salesforce account, consumes the Salesforce data and injects the data to the ESB sequence.
Now that you have configured the Salesforce streaming inbound endpoint, use the following ESB inbound endpoint configuration to retrieve account details from your Salesforce account.

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
* connection.salesforce.replay: replay **enable** or **disable**. If this enabled read from event id stored in registry DB or from the text file stored in the local machine.
* connection.salesforce.EventIDStoredFilePath:
    - when replay enabled, leave this property to empty if need to replay from last event id stored in registry DB (property-“eventID” resource path:connector/salesforce/event).
    - when replay enabled and specify a text file to replay onwards the stored event id from the file specified here.
    ```
    In case of failure default value will be used to retriev from current events. Create propety “eventID” in registry DB in resource path connector/salesforce/event. 
    ```
* connection.salesforce.packageVersion: The version of the salesforce API.
* connection.salesforce.salesforceObject : The name of the push topic or platform event that is added to the Salesforce account.
* connection.salesforce.loginEndpoint: The endpoint for the Salesforce account.
* connection.salesforce.userName:  The user name for accessing the Salesforce account.
* connection.salesforce.password: The password provided here is a concatenation of the user password and the security token provided by Salesforce.[information on creating a security token in Salesforce](https://help.salesforce.com/articleView?id=user_security_token.htm&type=5).
* connection.salesforce.waitTime: The time to connect to the Salesforce account(default : 5 * 1000 ms).
* connection.salesforce.connectionTimeout: The time to wait when connecting to the client(default : 20 * 1000 ms).
* connection.salesforce.soapApiVersion: The version of the salesforce soap API.

## Platform Event
[Define a platform event](https://developer.salesforce.com/docs/atlas.en-us.platform_events.meta/platform_events/platform_events_intro.htm) in the same way you define a custom object in salesforce.


In ESB configuration specify the event need to subscribe.
```
<parameter name="connection.salesforce.salesforceObject">/event/InvoiceStatementReading1s__e</parameter>
```
Go to the developer console of your Salesforce account and click on **Debug->Open Execute Anonymous Window.** Add the following entry in the Enter Apex Code window.See more [here](https://developer.salesforce.com/docs/atlas.en-us.platform_events.meta/platform_events/platform_events_publish_apex.htm).

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
Event will be triggered in the ESB real time.

## How to Contribute

  * Please report issues at [GitHub Issue](https://github.com/wso2-extensions/esb-inbound-salesforce/issues).
   
  * Send your contributions as pull requests to [master branch](https://github.com/wso2-extensions/esb-inbound-salesforce).

Also you can create a third party connector and publish in WSO2 Connector Store.

https://docs.wso2.com/display/ESBCONNECTORS/Creating+a+Third+Party+Connector+and+Publishing+in+WSO2+Connector+Store