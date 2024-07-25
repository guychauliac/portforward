package chabernac.portforward;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PortForward {
    private static final Logger LOGGER         = LogManager.getLogger( PortForward.class );
    private final int           myLocalPort;
    private final int           myDestinationPort;
    private final String        myDestinationHost;
    private final Object        LOCK           = new Object();

    private ServerSocket        myServerSocket = null;

    public PortForward( int aLocalPort, String aDestinationHost, int aDestinationPort ) {
        this.myLocalPort = aLocalPort;
        this.myDestinationPort = aDestinationPort;
        this.myDestinationHost = aDestinationHost;
    }

    public synchronized boolean start( ExecutorService anExecutorService ) {
        if ( !isStarted() ) {
            
            ProxySelector.setDefault(new ProxySelector() {
                public java.util.List<Proxy> select(URI uri) {
                    return java.util.Collections.emptyList();
                }
                public void connectFailed(URI uri, Socket socket, IOException e) {
                }
                @Override
                public void connectFailed( URI uri, SocketAddress sa, IOException ioe ) {
                    // TODO Auto-generated method stub
                    
                }
            });
            
            anExecutorService.execute( new ServerSocketHandler( anExecutorService ) );
            synchronized ( this.LOCK ) {
                try {
                    this.LOCK.wait( 5000L );
                } catch ( InterruptedException e ) {
                    e.printStackTrace();
                }
            }
        }
        return isStarted();
    }

    public synchronized void stop() {
        if ( isStarted() ) {
            try {
                this.myServerSocket.close();
            } catch ( IOException e ) {
                LOGGER.error( "Error occured while stopping portforward", e );
            } finally {
                this.myServerSocket = null;
            }
        }
    }

    public synchronized boolean isStarted() {
        return this.myServerSocket != null;
    }

    public String toString() {
        return this.myLocalPort + "-->" + this.myDestinationHost + ":" + this.myDestinationPort;
    }

    private class ServerSocketHandler implements Runnable {
        private final ExecutorService myExecutorService;

        public ServerSocketHandler( ExecutorService aExecutorService ) {
            this.myExecutorService = aExecutorService;
        }

        public void run() {
            try {
                myServerSocket = new ServerSocket( PortForward.this.myLocalPort );

                synchronized ( PortForward.this.LOCK ) {
                    PortForward.this.LOCK.notifyAll();
                }
                while ( true ) {
                    Socket theSocket = PortForward.this.myServerSocket.accept();
                    PortForward.LOGGER.debug( "Socket accepted: " + PortForward.this.toString() );
                    this.myExecutorService.execute( new PortForward.SocketHandler( theSocket, this.myExecutorService ) );
                }
            } catch ( Exception e ) {
                PortForward.LOGGER.error( "could not start portfward " + toString(), e );
            }
        }
    }

    private class StreamCopier implements Runnable {
        private static final Logger LOGGER         = LogManager.getLogger( StreamCopier.class );
        private final InputStream                 myInputStream;
        private final OutputStream                myOutputStream;
        private final ArrayBlockingQueue<Boolean> myQueue;

        public StreamCopier( InputStream anInputStream, OutputStream anOutputStream, ArrayBlockingQueue<Boolean> aQueue ) {
            this.myInputStream = anInputStream;
            this.myOutputStream = anOutputStream;
            this.myQueue = aQueue;
        }

        public void run() {
            byte[] theBuffer = new byte[ 1024 ];
            try {
                while ( true ) {
                    int theBytesRead = this.myInputStream.read( theBuffer );

                    if ( theBytesRead == -1 ) break;
                    this.myOutputStream.write( theBuffer, 0, theBytesRead );
                    this.myOutputStream.flush();
                    LOGGER.trace( "Copied {} bytes", theBytesRead);
                    LOGGER.trace(new String(theBuffer));
                }
            } catch ( Throwable localThrowable ) {
            }
            try {
                this.myQueue.put( Boolean.TRUE );
            } catch ( InterruptedException e ) {
                e.printStackTrace();
            }
        }
    }

    private class SocketHandler
        implements Runnable {
        private final Socket          mySocket;
        private final ExecutorService myExecutorService;

        public SocketHandler( Socket aSocket, ExecutorService anExecutorService ) {
            this.mySocket = aSocket;
            this.myExecutorService = anExecutorService;
        }

        public void run() {
            Socket theRemoteHost = null;
            try {
                theRemoteHost = new Socket( PortForward.this.myDestinationHost, PortForward.this.myDestinationPort );
                ArrayBlockingQueue<Boolean> theQueue = new ArrayBlockingQueue<Boolean>( 1 );
                this.myExecutorService.execute( new PortForward.StreamCopier( this.mySocket.getInputStream(), theRemoteHost.getOutputStream(), theQueue ) );
                this.myExecutorService.execute( new PortForward.StreamCopier( theRemoteHost.getInputStream(), this.mySocket.getOutputStream(), theQueue ) );
                theQueue.take();
            } catch ( Throwable e ) {
                PortForward.LOGGER.error( "An error occured in setting up connection to " + PortForward.this.myDestinationHost + ":" + PortForward.this.myDestinationPort, e );
            }
            try {
                this.mySocket.close();
            } catch ( Throwable e ) {
                e.printStackTrace();
            }
            try {
                if ( theRemoteHost != null ) {
                    theRemoteHost.close();
                }
            } catch ( Exception e ) {
                e.printStackTrace();
            }
        }
    }
}
