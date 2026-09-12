package com.joemo.razeredgefan;

interface IFanShellService {
    // Returns "<exitCode>|<combined stdout+stderr>".
    String run(String command);
    void destroy();
}
