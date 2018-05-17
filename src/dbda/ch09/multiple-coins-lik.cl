inline REAL multiple_coins_loglik(const uint data_len, const REAL* data, const uint dim, const REAL* p) {
    return binomial_log(data[0], p[0], data[1]) + binomial_log(data[2], p[1], data[3]);
}
