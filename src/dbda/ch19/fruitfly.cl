REAL ff_loglik(const uint data_len, const REAL* data, const uint dim, const REAL* x) {

    const REAL sigma = x[0];

    REAL a[5];
    REAL a_mean = 0.0f;
    for (uint i = 0; i < 5; i++) {
        a[i] = x[i+2];
        a_mean += a[i];
    }
    a_mean = a_mean / 5.0;

    const REAL bcov = x[7];
    const REAL xcov_mean = data[0];

    const REAL b0 = x[1] + a_mean - bcov * xcov_mean;

    const bool valid = (0.0f < sigma);

    const REAL scale = gaussian_log_scale(sigma);

    REAL res = 0.0;
        for (uint i = 0; i < data_len; i += 3) {
        const uint j = (uint)data[i*3+1];
        res += gaussian_log_unscaled(b0 + a[j] - a_mean + bcov * data[i*3+2],
                                     sigma, data[i*3+3]) + scale;
    }
    return res;
}

REAL ff_mcmc_logpdf(const uint data_len, const uint hyperparams_len, const REAL* params,
                    const uint dim, REAL* x) {
    REAL logp = uniform_log(params[0], params[1], x[0])
        + gaussian_log_unscaled(params[2], params[3], x[1])
        + gaussian_log_unscaled(params[9], params[10], x[dim-1]);
    for (uint i = 2; i < dim-1; i++) {
        logp += gaussian_log_unscaled(0, params[i+2], x[i]);
    }
    return logp;
}

REAL ff_logpdf(const uint data_len, const uint hyperparams_len, const REAL* params,
               const uint dim, REAL* x) {
    REAL logp = uniform_log(params[0], params[1], x[0])
        + gaussian_log(params[2], params[3], x[1])
        + gaussian_log(params[9], params[10], x[dim-1]);
    for (uint i = 2; i < dim-1; i++) {
        logp += gaussian_log(0, params[i+2], x[i]);
    }
    return logp;
}
