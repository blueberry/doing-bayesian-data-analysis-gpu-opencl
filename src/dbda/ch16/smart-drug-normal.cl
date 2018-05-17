REAL smart_drug_logpdf(const uint data_len, const uint hyperparams_len, const REAL* params,
                       const uint dim, REAL* x) {

    return gaussian_log(params[0], params[1], x[0])
        + uniform_log(params[2], params[3], x[1]);

}
