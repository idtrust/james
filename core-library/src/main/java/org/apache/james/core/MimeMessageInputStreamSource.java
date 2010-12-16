/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/



package org.apache.james.core;

import javax.mail.MessagingException;
import javax.mail.util.SharedFileInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.james.lifecycle.api.Disposable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Takes an input stream and creates a repeatable input stream source
 * for a MimeMessageWrapper.  It does this by completely reading the
 * input stream and saving that to a temporary file that should delete on exit,
 * or when this object is GC'd.
 *
 * @see MimeMessageWrapper
 *
 *
 */
public class MimeMessageInputStreamSource
    extends MimeMessageSource implements Disposable{

    private final List<InputStream> streams = new ArrayList<InputStream>();

    private OutputStream out;
    
    
    /**
     * A temporary file used to hold the message stream
     */
    private File file;

    /**
     * The full path of the temporary file
     */
    private String sourceId;

    /**
     * Construct a new MimeMessageInputStreamSource from an
     * <code>InputStream</code> that contains the bytes of a
     * MimeMessage.
     *
     * @param key the prefix for the name of the temp file
     * @param in the stream containing the MimeMessage
     *
     * @throws MessagingException if an error occurs while trying to store
     *                            the stream
     */
    public MimeMessageInputStreamSource(String key, InputStream in)
            throws MessagingException {
        super();
        //We want to immediately read this into a temporary file
        //Create a temp file and channel the input stream into it
        OutputStream fout = null;
        try {
            file = File.createTempFile(key, ".m64");
            fout = new BufferedOutputStream(new FileOutputStream(file));
            IOUtils.copy(in, fout);
            sourceId = file.getCanonicalPath();
        } catch (IOException ioe) {
            throw new MessagingException("Unable to retrieve the data: " + ioe.getMessage(), ioe);
        } finally {
            try {
                if (fout != null) {
                    fout.close();
                }
            } catch (IOException ioe) {
                // Ignored - logging unavailable to log this non-fatal error.
            }

            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ioe) {
                // Ignored - logging unavailable to log this non-fatal error.
            }
            
            // if sourceId is null while file is not null then we had
            // an IOxception and we have to clean the file.
            if (sourceId == null && file != null) {
                file.delete();
            }
        }
    }

    public MimeMessageInputStreamSource(String key) throws MessagingException {
        super();
        try {
            file = File.createTempFile(key, ".m64");
            sourceId = file.getCanonicalPath();
        } catch (IOException e) {
            throw new MessagingException("Unable to get canonical file path: " + e.getMessage(), e);
        } finally {
            // if sourceId is null while file is not null then we had
            // an IOxception and we have to clean the file.
            if (sourceId == null && file != null) {
                file.delete();
            }
        }
    }
    
    /**
     * Returns the unique identifier of this input stream source
     *
     * @return the unique identifier for this MimeMessageInputStreamSource
     */
    public String getSourceId() {
        return sourceId;
    }

    /**
     * Get an input stream to retrieve the data stored in the temporary file
     *
     * @return a <code>BufferedInputStream</code> containing the data
     */
    public synchronized InputStream getInputStream() throws IOException {
        SharedFileInputStream in = new SharedFileInputStream(file);
        streams.add(in);
        return in;
    }

    /**
     * Get the size of the temp file
     *
     * @return the size of the temp file
     *
     * @throws IOException if an error is encoutered while computing the size of the message
     */
    public long getMessageSize() throws IOException {
        return file.length();
    }

    /**
     * @return
     * @throws FileNotFoundException
     */
    public synchronized OutputStream getWritableOutputStream() throws FileNotFoundException {
        if (out == null) {
            out = new FileOutputStream(file);
        }
        return out;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.core.MimeMessageSource#disposeSource()
     */
    public void dispose() {
     // explicit close all streams
        for (int i = 0; i < streams.size(); i++) {
            IOUtils.closeQuietly(streams.get(i));
        }
        IOUtils.closeQuietly(out);
        out = null;
        
        FileUtils.deleteQuietly(file);
        file = null;
    }


}
