REAL smart_drug_mcmc_logpdf(const uint data_len, const uint hyperparams_len, const REAL* params,
                            const uint dim, REAL* x) {

    return exponential_log_unscaled(params[0], x[0] - 1)
        + gaussian_log_unscaled(params[1], params[2], x[1])
        + uniform_log(params[3], params[4], x[2]);

}

REAL smart_drug_logpdf(const uint data_len, const uint hyperparams_len, const REAL* params,
                       const uint dim, REAL* x) {

    return exponential_log(params[0], x[0] - 1)
        + gaussian_log(params[1], params[2], x[1])
        + uniform_log(params[3], params[4], x[2]);

}
