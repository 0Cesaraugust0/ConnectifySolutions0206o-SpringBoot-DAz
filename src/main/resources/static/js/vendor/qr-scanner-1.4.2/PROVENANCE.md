# qr-scanner 1.4.2

Source: https://registry.npmjs.org/qr-scanner/-/qr-scanner-1.4.2.tgz
Upstream: https://github.com/nimiq/qr-scanner
License: MIT, included in LICENSE.

The two JavaScript files are copied without modification from this exact npm
package. Keep them together: the ES module dynamically imports its neighboring
worker module when the browser lacks a usable native QR detector. No CDN is used.

SHA-256:

    package tarball: c4320730bc62f77bd53905bad303cf6b257b4a125f6a8f1268c42e2f818a4aa1
    qr-scanner.min.js: 0564e4cf84b94cac80213b4f779bd215bb0876a91c19df28baf97766f4fd5484
    qr-scanner-worker.min.js: f4d8445f5a15c4e5f71a8c8c062c6443f09db370b8d3fe1fbdc7fe7889630d14
    LICENSE: f9e6b44d80d0eb44442c7e66af8a756709a61e4d9adf0a54b8951a92ece734e2

Camera access is requested only through a user click. Frames stay on the device;
only the decoded ticket code is posted when the operator submits an action.
