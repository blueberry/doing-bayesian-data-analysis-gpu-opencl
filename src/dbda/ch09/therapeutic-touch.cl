REAL touch_logpdf(const uint data_len, const uint hyperparams_len, const REAL* params,
                  const uint dim, REAL* x) {
    const REAL a = params[0];
    const REAL b = params[1];
    const REAL omega = x[dim - 2];
    const REAL kappam2 = x[dim - 1];
    const REAL ak = omega * kappam2 + 1.0f;
    const REAL bk = (1 - omega) * kappam2 + 1.0f;

    bool valid = (0.0f <= omega) & (omega <= 1.0f) && (0.0f <= kappam2);
    REAL logp = beta_log(a, b, omega) + gamma_log(params[2], params[3], kappam2)
        - (dim - 2) * lbeta(ak, bk);

    for (uint i = 0; i < (dim - 2); i++) {
        const REAL theta = x[i];
        valid = valid && (0.0f <= theta) && (theta <= 1.0f);
        logp += beta_log_unscaled(ak, bk, theta);
    }

    return valid ? logp : NAN;

}
