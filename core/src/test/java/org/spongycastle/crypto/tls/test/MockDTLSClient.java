package org.spongycastle.crypto.tls.test;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Vector;

import org.spongycastle.asn1.x509.Certificate;
import org.spongycastle.crypto.tls.AlertLevel;
import org.spongycastle.crypto.tls.CertificateRequest;
import org.spongycastle.crypto.tls.CipherSuite;
import org.spongycastle.crypto.tls.ClientCertificateType;
import org.spongycastle.crypto.tls.DefaultTlsClient;
import org.spongycastle.crypto.tls.MaxFragmentLength;
import org.spongycastle.crypto.tls.ProtocolVersion;
import org.spongycastle.crypto.tls.SignatureAlgorithm;
import org.spongycastle.crypto.tls.SignatureAndHashAlgorithm;
import org.spongycastle.crypto.tls.TlsAuthentication;
import org.spongycastle.crypto.tls.TlsCredentials;
import org.spongycastle.crypto.tls.TlsExtensionsUtils;
import org.spongycastle.crypto.tls.TlsSession;
import org.spongycastle.util.Arrays;
import org.spongycastle.util.encoders.Hex;

public class MockDTLSClient
    extends DefaultTlsClient
{
    protected TlsSession session;

    public MockDTLSClient(TlsSession session)
    {
        this.session = session;
    }

    public TlsSession getSessionToResume()
    {
        return this.session;
    }

    public void notifyAlertRaised(short alertLevel, short alertDescription, String message, Exception cause)
    {
        PrintStream out = (alertLevel == AlertLevel.fatal) ? System.err : System.out;
        out.println("DTLS client raised alert (AlertLevel." + alertLevel + ", AlertDescription." + alertDescription
            + ")");
        if (message != null)
        {
            out.println(message);
        }
        if (cause != null)
        {
            cause.printStackTrace(out);
        }
    }

    public void notifyAlertReceived(short alertLevel, short alertDescription)
    {
        PrintStream out = (alertLevel == AlertLevel.fatal) ? System.err : System.out;
        out.println("DTLS client received alert (AlertLevel." + alertLevel + ", AlertDescription." + alertDescription
            + ")");
    }

    public ProtocolVersion getClientVersion()
    {
        return ProtocolVersion.DTLSv12;
    }

    public ProtocolVersion getMinimumVersion()
    {
        return ProtocolVersion.DTLSv10;
    }

    public Hashtable getClientExtensions() throws IOException
    {
        Hashtable clientExtensions = super.getClientExtensions();
        if (clientExtensions == null)
        {
            clientExtensions = new Hashtable();
        }
        TlsExtensionsUtils.addMaxFragmentLengthExtension(clientExtensions, MaxFragmentLength.pow2_9);
        TlsExtensionsUtils.addTruncatedHMacExtension(clientExtensions);
        return clientExtensions;
    }

    public void notifyServerVersion(ProtocolVersion serverVersion) throws IOException
    {
        super.notifyServerVersion(serverVersion);

        System.out.println("Negotiated " + serverVersion);
    }

    public TlsAuthentication getAuthentication()
        throws IOException
    {
        return new TlsAuthentication()
        {
            public void notifyServerCertificate(org.spongycastle.crypto.tls.Certificate serverCertificate)
                throws IOException
            {
                Certificate[] chain = serverCertificate.getCertificateList();
                System.out.println("Received server certificate chain of length " + chain.length);
                for (int i = 0; i != chain.length; i++)
                {
                    Certificate entry = chain[i];
                    // TODO Create fingerprint based on certificate signature algorithm digest
                    System.out.println("    fingerprint:SHA-256 " + TlsTestUtils.fingerprint(entry) + " ("
                        + entry.getSubject() + ")");
                }
            }

            public TlsCredentials getClientCredentials(CertificateRequest certificateRequest)
                throws IOException
            {
                short[] certificateTypes = certificateRequest.getCertificateTypes();
                if (certificateTypes == null || !Arrays.contains(certificateTypes, ClientCertificateType.rsa_sign))
                {
                    return null;
                }

                SignatureAndHashAlgorithm signatureAndHashAlgorithm = null;
                Vector sigAlgs = certificateRequest.getSupportedSignatureAlgorithms();
                if (sigAlgs != null)
                {
                    for (int i = 0; i < sigAlgs.size(); ++i)
                    {
                        SignatureAndHashAlgorithm sigAlg = (SignatureAndHashAlgorithm)
                            sigAlgs.elementAt(i);
                        if (sigAlg.getSignature() == SignatureAlgorithm.rsa)
                        {
                            signatureAndHashAlgorithm = sigAlg;
                            break;
                        }
                    }

                    if (signatureAndHashAlgorithm == null)
                    {
                        return null;
                    }
                }

                // TODO Create a distinct client certificate for use here
                return TlsTestUtils.loadSignerCredentials(context, new String[] { "x509-server.pem", "x509-ca.pem" },
                    "x509-server-key.pem", signatureAndHashAlgorithm);
            }
        };
    }

    public void notifyHandshakeComplete() throws IOException
    {
        super.notifyHandshakeComplete();

        TlsSession newSession = context.getResumableSession();
        if (newSession != null)
        {
            byte[] newSessionID = newSession.getSessionID();
            String hex = Hex.toHexString(newSessionID);

            if (this.session != null && Arrays.areEqual(this.session.getSessionID(), newSessionID))
            {
                System.out.println("Resumed session: " + hex);
            }
            else
            {
                System.out.println("Established session: " + hex);
            }

            this.session = newSession;
        }
    }
}
