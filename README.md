# resumable-file-upload 

Possible implementation of resumable file upload using akka-http and akka-persistence.

### Initiate file upload

Request:
```
POST /files
{
    "name": ${Filename with extension}
    "media": ${Media Type (Optional)}
}
```

Response:
```
200 OK
Location "$uploadLink"
```
where 
`uploadLink` = `configuredPrefix/upload?upload_token=uploadToken`

### Upload file

```
PUT /upload?upload_token=uploadToken
Content-Type: ${Content type}

bytes
```

### Resume an interrupted upload

Query the current status of the upload by issuing an empty PUT request to the upload URI.
For this request, the HTTP headers should include a `Content-Range` header indicating that the current position in the file is unknown.  For example, set the `Content-Range` to `*/2000000` if your total file length is 2,000,000. If you don't know the full size of the file, set the `Content-Range` to `*/*`.

Request:
```
PUT /upload?upload_token=uploadToken
Content-Range: bytes */2000000
```

The server uses the `Range` header in its response to specify which bytes it has received so far.  For example, a `Range` header of 0-42 indicates that the first 43 bytes of the file have been received.

Response:
```
200 Ok
Range: 0-42
```

Resume the upload from the point where it left off.

```
PUT /upload?upload_token=uploadToken
Content-Range: bytes 43-1999999/2000000

bytes 43-1999999
```

