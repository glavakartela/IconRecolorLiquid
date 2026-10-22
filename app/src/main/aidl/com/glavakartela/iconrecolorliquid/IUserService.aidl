package com.glavakartela.iconrecolorliquid;

interface IUserService {
    // Required by Shizuku (the transaction code is fixed)
    void destroy() = 16777114;

    String exec(String command) = 1;
}
