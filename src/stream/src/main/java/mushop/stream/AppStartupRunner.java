package mushop.stream;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.oracle.bmc.streaming.StreamClient;
import com.oracle.bmc.streaming.model.CreateCursorDetails;
import com.oracle.bmc.streaming.model.CreateCursorDetails.Type;
import com.oracle.bmc.streaming.model.Message;
import com.oracle.bmc.streaming.requests.CreateCursorRequest;
import com.oracle.bmc.streaming.requests.GetMessagesRequest;
import com.oracle.bmc.streaming.responses.CreateCursorResponse;
import com.oracle.bmc.streaming.responses.GetMessagesResponse;

import mushop.stream.configuration.OciStreamsConfiguration;
import mushop.stream.entities.Shipment;
import shaded.com.oracle.oci.javasdk.com.google.common.util.concurrent.Uninterruptibles;

@Component
public class AppStartupRunner implements ApplicationRunner {
 
	@Autowired
	private OciStreamsConfiguration streamConfig;
	
	@Autowired
	private ShippingTaskHandler shippingTaskHandler;
	
	@Autowired
	private Environment env;
 
    @Override
    public void run(ApplicationArguments args) throws Exception {

    	try {
			streamConfig.initConnection();
		} catch (Exception e) {
			e.printStackTrace();
		}

    	// If running unit tests the job.autorun.enabled property is set to false
    	// This enables tests to complete since consumeShippingStream has infinite loop
    	String autorun = env.getProperty("job.autorun.enabled");
    	System.out.println("job.autorun.enabled: " + autorun);
    	if (autorun==null) {
    		consumeShippingStream();
    	} 
    }
    
    private void consumeShippingStream() {
		if (streamConfig == null) {
			System.out.println("streamConfig is null");
			return;
		}

		if (streamConfig.mockMode()) {
			// Nothing to do if in mock mode.
			return;
		}

        StreamClient streamClient = streamConfig.getStreamClient();
        String streamId = streamConfig.getStreamId();
        
        String partitionCursor = getCursorByPartition(streamClient, streamId, "0");
        forEverMessageLoop(streamClient, streamId, partitionCursor);
	}
	
	/*
	 * 
	 */
	private void forEverMessageLoop(StreamClient streamClient, String streamId, String initialCursor) {
        String cursor = initialCursor;
        Shipment shipment = null;
        
        for (;;) {
            GetMessagesRequest getRequest =
                    GetMessagesRequest.builder()
                            .streamId(streamId)
                            .cursor(cursor)
                            .limit(10)
                            .build();

            GetMessagesResponse getResponse = streamClient.getMessages(getRequest);
            //System.out.println(String.format("Read %s messages.", getResponse.getItems().size()));
            for (Message message : getResponse.getItems()) {
            	String name = new String(message.getValue(), UTF_8);
            	// first try to build json object since the message is expected to be in json format
            	try {
            		JSONObject json = new JSONObject( name );
            		shipment = new Shipment(json);
            	} catch (JSONException e) {
            		// if it fails, create a shipment from just the name
            		System.out.println(e.getMessage());
            		shipment = new Shipment(null, name);
            	}
            	System.out.println(shipment.toString());
				shippingTaskHandler.handleMessage(shipment);
			}

            // getMessages is a throttled method; clients should retrieve sufficiently large message
            // batches, as to avoid too many http requests.
            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

            // use the next-cursor for iteration
            cursor = getResponse.getOpcNextCursor();
        }
    }
	
	
	/*
	 * 
	 */
	private String getCursorByPartition(StreamClient streamClient, String streamId, String partition) {
		
		System.out.println(String.format("Creating a cursor for partition %s.", partition));

		CreateCursorDetails cursorDetails =
				CreateCursorDetails
				.builder()
				.partition(partition)
				.type(Type.Latest)
				//.type(Type.TrimHorizon)
				//.type(Type.AtOffset)
				.build();

		CreateCursorRequest createCursorRequest =
				CreateCursorRequest.builder()
				.streamId(streamId)
				.createCursorDetails(cursorDetails)
				.build();

		CreateCursorResponse cursorResponse = streamClient.createCursor(createCursorRequest);
		return cursorResponse.getCursor().getValue();
	}
}

