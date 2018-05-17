REAL touch_loglik(const uint data_len, const REAL* data, const uint dim, const REAL* p) {
    REAL loglik = 0.0f;
    for (uint i = 0; i < (dim - 2); i++) {
        const REAL theta = p[i];
        loglik += binomial_log_unscaled(data[2*i], theta, data[2*i+1]);
    }
    return loglik;

}
