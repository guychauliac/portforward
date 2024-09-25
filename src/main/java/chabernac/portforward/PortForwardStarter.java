package chabernac.portforward;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PortForwardStarter {
    public static void main( String[] args ) throws IOException {
        System.out.println( "Starting portforward" );

        String config = "config.txt";
        if ( args.length > 0 ) {
            config = args[ 0 ];
        }

        List<PortForward> thePortForwards = PortForwardConfiguration.read( config );

        shutDownAllPortForwardsOnExit( thePortForwards );
        startPortForwards( thePortForwards );
    }

    private static void shutDownAllPortForwardsOnExit( List<PortForward> thePortForwards ) {
        System.out.println( "Nr of porforwards configurations: '" + thePortForwards.size() + "'" );
        Runtime.getRuntime().addShutdownHook( new Thread() {
            public void run() {
                for ( PortForward theForward : thePortForwards ) {
                    theForward.stop();
                }
            }
        } );

    }

    private static void startPortForwards( List<PortForward> thePortForwards ) throws IOException {
        ExecutorService theExecutorService = Executors.newCachedThreadPool();
        for ( PortForward theForward : thePortForwards ) {
            boolean isStarted = theForward.start( theExecutorService );
            System.out.println( theForward.toString() + " " + isStarted );
        }

        System.in.read();
    }
}
