ARG JITSI_REPO=jitsi
ARG BASE_TAG=latest
FROM ${JITSI_REPO}/base-java:${BASE_TAG}

LABEL numax <numax.genew.com>
ENV DEBIAN_FRONTEND noninteractive
#ENV https_proxy http://10.8.106.127:7890
#ENV http_proxy http://10.8.106.127:7890
#ARG CHROME_RELEASE=latest
#ARG CHROMEDRIVER_MAJOR_RELEASE=latest
ARG CHROME_RELEASE=114.0.5735.90
ARG CHROMEDRIVER_MAJOR_RELEASE=114

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

#RUN sed -i "s@http://deb.debian.org@http://mirrors.aliyun.com@g" /etc/apt/sources.list
# 替换清华站点
RUN sed -i "s@http://deb.debian.org@http://mirrors.tuna.tsinghua.edu.cn@g" /etc/apt/sources.list && \
    printf "export http_proxy=http://10.8.106.127:7890\nexport https_proxy=http://10.8.106.127:7890\n" >> /etc/profile && \
    source /etc/profile &>/dev/null



RUN apt-get update && \
	apt-get install --assume-yes --no-install-recommends -y \
			wget curl tcpdump net-tools telnet vim openssh-server \
			build-essential libv4l-dev libsdl2-dev libavcodec-dev \
			libavdevice-dev libavfilter-dev libavformat-dev \
			libavresample-dev libavutil-dev libswresample-dev \
			libswscale-dev libasound2-dev libopus-dev libvpx-dev && \
	apt-get clean && \
	rm -Rf /var/lib/apt/lists/*

#RUN apt-get update && \
#	apt-get install --assume-yes --no-install-recommends -y \
#			git && \
#	apt-get clean && \
#	rm -Rf /var/lib/apt/lists/*

#RUN git clone -b jibri-2.10-dev1 https://github.com/jitsi/pjproject && \
#	cd pjproject  && \
#	./configure && \
#	make dep  && \
#	make

#RUN \
#	rm -rf /var/lib/apt/lists/* \
#	&& apt-get update && apt-get install -y wget make build-essential libasound2-dev && rm -rf  /var/lib/apt/lists/*
#RUN wget -e "https_proxy=https://10.8.6.39:10809" https://github.com/pjsip/pjproject/archive/refs/tags/2.12.tar.gz \
#	&& tar zxvf 2.12.tar.gz -C /usr/share && rm 2.12.tar.gz \
#	&& apt-cleanup
#WORKDIR /usr/share/pjproject-2.12
#RUN ./configure && make dep && make
#RUN ln -s /usr/share/pjproject-2.12/pjsip-apps/bin/pjsua-x86_64-unknown-linux-gnu /usr/local/bin/pjsua

WORKDIR /

RUN apt-get update && \
	apt-get install --assume-yes --no-install-recommends -y \
			unzip libx11-xcb1 libpulse0 ffmpeg alsa-utils icewm  \
		    xserver-xorg-video-dummy  ruby-hocon libgl1-mesa-dri procps && \
	apt-get clean && \
	rm -Rf /var/lib/apt/lists/*

# RUN \
# 	apt-dpkg-wrap apt-get update \
# 	&& apt-dpkg-wrap apt-get install -y libgl1-mesa-dri procps \
# 	&& apt-cleanup

RUN \
	[ "${CHROME_RELEASE}" = "latest" ] \
	&& wget -q https://dl-ssl.google.com/linux/linux_signing_key.pub -O /etc/apt/trusted.gpg.d/google.asc \
	&& echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list \
	&& apt-dpkg-wrap apt-get update \
	&& apt-dpkg-wrap apt-get install -y google-chrome-stable \
	&& apt-cleanup \
	|| true

RUN \
    [ "${CHROME_RELEASE}" != "latest" ] \
    && curl -4so "/tmp/google-chrome-stable_${CHROME_RELEASE}-1_amd64.deb" "http://dl.google.com/linux/chrome/deb/pool/main/g/google-chrome-stable/google-chrome-stable_${CHROME_RELEASE}-1_amd64.deb" \
	&& apt-dpkg-wrap apt-get update \
    && apt-dpkg-wrap apt-get install -y "/tmp/google-chrome-stable_${CHROME_RELEASE}-1_amd64.deb" \
	&& apt-cleanup \
	|| true

#RUN \
#	[ "${CHROMEDRIVER_MAJOR_RELEASE}" = "latest" ] \
#	&& CHROMEDRIVER_RELEASE="$(curl -4Ls https://chromedriver.storage.googleapis.com/LATEST_RELEASE)" \
#	|| CHROMEDRIVER_RELEASE="$(curl -4Ls https://chromedriver.storage.googleapis.com/LATEST_RELEASE_${CHROMEDRIVER_MAJOR_RELEASE})" \
#	&& curl -4Ls "https://chromedriver.storage.googleapis.com/${CHROMEDRIVER_RELEASE}/chromedriver_linux64.zip" \
#    && wget -N http://chromedriver.storage.googleapis.com/${CHROMEDRIVER_RELEASE}/chromedriver_linux64.zip \
#    | zcat >> /usr/bin/chromedriver \
#	&& chmod +x /usr/bin/chromedriver \
#	&& chromedriver --version

RUN \
	[ "${CHROMEDRIVER_MAJOR_RELEASE}" = "latest" ] \
	&& CHROMEDRIVER_RELEASE="$(curl -4Ls https://chromedriver.storage.googleapis.com/LATEST_RELEASE)" \
	|| CHROMEDRIVER_RELEASE="$(curl -4Ls https://chromedriver.storage.googleapis.com/LATEST_RELEASE_${CHROMEDRIVER_MAJOR_RELEASE})" \
    && wget -N http://chromedriver.storage.googleapis.com/${CHROMEDRIVER_RELEASE}/chromedriver_linux64.zip -P ~/ \
    && unzip ~/chromedriver_linux64.zip -d ~/ \
    && rm ~/chromedriver_linux64.zip \
    && mv -f ~/chromedriver /usr/local/bin/chromedriver \
    && chown root:root /usr/local/bin/chromedriver \
    && chmod 0755 /usr/local/bin/chromedriver



RUN \
        apt-dpkg-wrap apt-get update \
        && apt-dpkg-wrap apt-get install -y jitsi-upload-integrations jq fonts-noto-cjk pulseaudio \
        && apt-cleanup

RUN mkdir /debs
COPY build/*.deb ./debs
RUN dpkg -i ./debs/jibri_*.deb
RUN rm -rf ./debs

COPY rootfs /

VOLUME /config

# 时区设置
RUN cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo 'Asia/Shanghai' >/etc/timezone

RUN ln -s /pjproject/pjsip-apps/bin/pjsua-x86_64-unknown-linux-gnu /usr/local/bin/pjsua

#COPY ./finalize_recording.sh /etc/jitsi/jibri
RUN sed -i '/_proxy=/d' /etc/profile && source /etc/profile

COPY run.sh /run.sh
RUN chmod +x /run.sh

CMD /run.sh