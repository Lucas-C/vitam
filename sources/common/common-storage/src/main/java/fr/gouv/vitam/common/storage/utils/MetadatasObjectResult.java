/**
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 */
package fr.gouv.vitam.common.storage.utils;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MetadatasObjectResult {

    @JsonProperty("objectName")
    private String objectName;
    @JsonProperty("type")
    private String type;
    @JsonProperty("digest")
    private String digest;
    @JsonProperty("fileSize")
    private long fileSize;
    @JsonProperty("fileOwner")
    private String fileOwner;
    @JsonProperty("lastAccessDate")
    private String lastAccessDate;
    @JsonProperty("lastModifiedDate")
    private String lastModifiedDate;  
    
    /**
     * empty constructor
     */
    public MetadatasObjectResult(){
        this.objectName = null;
        this.type = null;
        this.digest = null;
        this.fileSize = 0;
        this.fileOwner = null;
        this.lastAccessDate = null;
        this.lastModifiedDate = null;
    }
    
    /**
     * Constructor to initialize the needed parameters for get metadata results
     * 
     * @param object_name
     * @param type
     * @param digest
     * @param fileSize
     * @param fileOwner
     * @param lastAccessDate
     * @param lastModifiedDate
     */
    public MetadatasObjectResult(String object_name, String type, String digest, long file_size, String file_owner,
        String last_access_date, String last_modified_date) {
        super();
        this.objectName = object_name;
        this.type = type;
        this.digest = digest;
        this.fileSize = file_size;
        this.fileOwner = file_owner;
        this.lastAccessDate = last_access_date;
        this.lastModifiedDate = last_modified_date;
    }
    
    /**
     * @return object name
     */
    public String getObjectName() {
        return objectName;
    }

    /**
     * @param objectName
     * @return MetadatasObjectResult
     */
    public MetadatasObjectResult setObjectName(String objectName) {
        this.objectName = objectName;
        return this;
    }

    /**
     * @return type
     */
    public String getType() {
        return type;
    }

    /**
     * @param type
     * @return MetadatasObjectResult
     */
    public MetadatasObjectResult setType(String type) {
        this.type = type;
        return this;
    }

    /**
     * @return digest
     */
    public String getDigest() {
        return digest;
    }

    /**
     * @param digest
     * @return MetadatasObjectResult
     */
    public MetadatasObjectResult setDigest(String digest) {
        this.digest = digest;
        return this;
    }

    /**
     * @return
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * @param fileSize
     * @return MetadatasObjectResult
     */
    public MetadatasObjectResult setFileSize(long fileSize) {
        this.fileSize = fileSize;
        return this;
    }

    /**
     * @return file owner
     */
    public String getFileOwner() {
        return fileOwner;
    }

    /**
     * @param fileOwner
     * @return MetadatasObjectResult
     */
    public MetadatasObjectResult setFileOwner(String fileOwner) {
        this.fileOwner = fileOwner;
        return this;
    }

    /**
     * @return file's last access date
     */
    public String getLastAccessDate() {
        return lastAccessDate;
    }

    /**
     * @param lastAccessDate
     * @return MetadatasObjectResult
     */
    public MetadatasObjectResult setLastAccessDate(String lastAccessDate) {
        this.lastAccessDate = lastAccessDate;
        return this;
    }

    /**
     * @return file's last modifiedDate
     */
    public String getLastModifiedDate() {
        return lastModifiedDate;
    }

    /**
     * @param lastModifiedDate
     * @return MetadatasObjectResult
     */
    public MetadatasObjectResult setLastModifiedDate(String lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
        return this;
    }
    
}