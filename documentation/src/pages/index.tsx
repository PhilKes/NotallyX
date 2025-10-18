import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import Heading from '@theme/Heading';

import styles from './index.module.css';

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  const image = require('@site/static/img/logo.png').default
  return (
    <header className={clsx('hero', styles.heroBanner)}>
      <div className="container">
        <div className="text--center">
          <img src={image} alt="NotallyX Logo" />
        </div>
        <Heading as="h1" className="hero__title">
          {siteConfig.title}
        </Heading>
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link
            className="button button--secondary button--lg"
            to="/docs/get-started">
            Get Started 📝
          </Link>
        </div>
        <div style={{display: 'flex', flexWrap: 'wrap', justifyContent: 'center', alignItems: 'center', marginTop: '20px'}}>
          <a href='https://play.google.com/store/apps/details?id=com.philkes.notallyx&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1' style={{margin: '10px'}}>
            <img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' style={{height: '80px'}}/>
          </a>
          <a href="https://f-droid.org/en/packages/com.philkes.notallyx" style={{margin: '10px'}}>
            <img alt='Get it on F-Droid' src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png' style={{height: '80px'}} />
          </a>
          <a href="https://apt.izzysoft.de/fdroid/index/apk/com.philkes.notallyx" style={{margin: '10px'}}>
            <img alt='Get it on IzzyOnDroid' src='https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png' style={{height: '80px'}} />
          </a>
        </div>
      </div>
    </header>
  );
}

export default function Home(): ReactNode {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      description="NotallyX - A simple and minimalistic open source notes app">
      <HomepageHeader />
      <main>
        <HomepageFeatures />
      </main>
    </Layout>
  );
}
