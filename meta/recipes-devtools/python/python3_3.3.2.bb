require recipes-devtools/python/python.inc

DEPENDS = "python3-native libffi bzip2 db gdbm openssl readline sqlite3 zlib"
PR = "${INC_PR}.0"
PYTHON_MAJMIN = "3.3"
PYTHON_BINABI= "${PYTHON_MAJMIN}m"
DISTRO_SRC_URI ?= "file://sitecustomize.py"
DISTRO_SRC_URI_linuxstdbase = ""
SRC_URI = "http://www.python.org/ftp/python/${PV}/Python-${PV}.tar.bz2 \
file://12-distutils-prefix-is-inside-staging-area.patch \
file://000-cross-compile.patch \
file://020-dont-compile-python-files.patch \
file://030-fixup-include-dirs.patch \
file://070-dont-clean-ipkg-install.patch \
file://080-distutils-dont_adjust_files.patch \
file://110-enable-zlib.patch \
file://130-readline-setup.patch \
file://150-fix-setupterm.patch \
file://0001-h2py-Fix-issue-13032-where-it-fails-with-UnicodeDeco.patch \
${DISTRO_SRC_URI} \
"

SRC_URI += "\
            file://03-fix-tkinter-detection.patch \
            file://04-default-is-optimized.patch \
            file://avoid_warning_about_tkinter.patch \
            file://06-ctypes-libffi-fix-configure.patch \
            file://remove_sqlite_rpath.patch \
            file://cgi_py.patch \
            file://host_include_contamination.patch \
            file://python-3.3-multilib.patch \
            file://shutil-follow-symlink-fix.patch \
            file://sysroot-include-headers.patch \
           "
SRC_URI[md5sum] = "7dffe775f3bea68a44f762a3490e5e28"
SRC_URI[sha256sum] = "f77202fe2bbc203205d7bd6e8452567fa1d9bc97f6957c9ef753d9a36cdcbe9a"

LIC_FILES_CHKSUM = "file://LICENSE;md5=a7e738bb078a33a825ce4a0282f8cb31"

S = "${WORKDIR}/Python-${PV}"

inherit autotools multilib_header python3native pkgconfig

CONFIGUREOPTS += " --with-system-ffi "

CACHED_CONFIGUREVARS = "ac_cv_have_chflags=no \
                ac_cv_have_lchflags=no \
                ac_cv_have_long_long_format=yes \
                ac_cv_buggy_getaddrinfo=no \
                ac_cv_file__dev_ptmx=yes \
                ac_cv_file__dev_ptc=no \
"
# The 3 lines below are copied from the libffi recipe, ctypes ships its own copy of the libffi sources
#Somehow gcc doesn't set __SOFTFP__ when passing -mfloatabi=softp :(
TARGET_CC_ARCH_append_armv6 = " -D__SOFTFP__"
TARGET_CC_ARCH_append_armv7a = " -D__SOFTFP__"
TARGET_CC_ARCH += "-DNDEBUG -fno-inline"
EXTRA_OEMAKE += "CROSS_COMPILE=yes"

do_configure_prepend() {
	rm -f ${S}/Makefile.orig
	autoreconf -Wcross --verbose --install --force --exclude=autopoint Modules/_ctypes/libffi || bbnote "_ctypes failed to autoreconf"
}

do_compile() {
        # regenerate platform specific files, because they depend on system headers
        cd Lib/plat-linux*
        include=${STAGING_INCDIR} ${STAGING_BINDIR_NATIVE}/python3-native/python3 \
                ${S}/Tools/scripts/h2py.py -i '(u_long)' \
                ${STAGING_INCDIR}/dlfcn.h \
                ${STAGING_INCDIR}/linux/cdrom.h \
                ${STAGING_INCDIR}/netinet/in.h \
                ${STAGING_INCDIR}/sys/types.h
        sed -e 's,${STAGING_DIR_HOST},,g' -i *.py
        cd -

	#
	# Copy config.h and an appropriate Makefile for distutils.sysconfig,
	# which laters uses the information out of these to compile extensions
	#
	# The following part (until python compilation) should probably moved to an
	# -initial recipe to handle staging better
	#
	install -d ${STAGING_INCDIR}/python${PYTHON_BINABI}/
	install -d ${STAGING_LIBDIR}/python${PYTHON_MAJMIN}/config/
	install -m 0644 pyconfig.h ${STAGING_INCDIR}/python${PYTHON_BINABI}/

	# remove hardcoded ccache, see http://bugs.openembedded.net/show_bug.cgi?id=4144
	sed -i -e s,ccache,'$(CCACHE)', Makefile

	# remove any bogus LD_LIBRARY_PATH
	sed -i -e s,RUNSHARED=.*,RUNSHARED=, Makefile

	if [ ! -f Makefile.orig ]; then
		install -m 0644 Makefile Makefile.orig
	fi
	sed -i -e 's,^LDFLAGS=.*,LDFLAGS=-L. -L${STAGING_LIBDIR},g' \
		-e 's,libdir=${libdir},libdir=${STAGING_LIBDIR},g' \
		-e 's,libexecdir=${libexecdir},libexecdir=${STAGING_DIR_HOST}${libexecdir},g' \
		-e 's,^LIBDIR=.*,LIBDIR=${STAGING_LIBDIR},g' \
		-e 's,includedir=${includedir},includedir=${STAGING_INCDIR},g' \
		-e 's,^INCLUDEDIR=.*,INCLUDE=${STAGING_INCDIR},g' \
		-e 's,^CONFINCLUDEDIR=.*,CONFINCLUDE=${STAGING_INCDIR},g' \
		Makefile
	install -m 0644 Makefile ${STAGING_LIBDIR}/python${PYTHON_MAJMIN}/config/
	# save copy of it now, because if we do it in do_install and 
	# then call do_install twice we get Makefile.orig == Makefile.sysroot
	install -m 0644 Makefile Makefile.sysroot

	export CROSS_COMPILE="${TARGET_PREFIX}"
	export PYTHONBUILDDIR="${S}"
	oe_runmake HOSTPGEN=${STAGING_BINDIR_NATIVE}/python3-native/pgen \
		HOSTPYTHON=${STAGING_BINDIR_NATIVE}/python3-native/python3 \
		STAGING_LIBDIR=${STAGING_LIBDIR} \
		STAGING_BASELIBDIR=${STAGING_BASELIBDIR} \
		STAGING_INCDIR=${STAGING_INCDIR} \
		BUILD_SYS=${BUILD_SYS} HOST_SYS=${HOST_SYS} \
		LIB=${baselib} \
		ARCH=${TARGET_ARCH} \
		OPT="${CFLAGS}" libpython3.so

	oe_libinstall -so libpython${PYTHON_BINABI} ${STAGING_LIBDIR}

	oe_runmake HOSTPGEN=${STAGING_BINDIR_NATIVE}/python-native3/pgen \
		HOSTPYTHON=${STAGING_BINDIR_NATIVE}/python-native3/python3 \
		STAGING_LIBDIR=${STAGING_LIBDIR} \
		STAGING_INCDIR=${STAGING_INCDIR} \
		STAGING_BASELIBDIR=${STAGING_BASELIBDIR} \
		BUILD_SYS=${BUILD_SYS} HOST_SYS=${HOST_SYS} \
		LIB=${baselib} \
		ARCH=${TARGET_ARCH} \
		OPT="${CFLAGS}"
}

do_install() {
	# make install needs the original Makefile, or otherwise the inclues would
	# go to ${D}${STAGING...}/...
	install -m 0644 Makefile.orig Makefile

	export CROSS_COMPILE="${TARGET_PREFIX}"
	export PYTHONBUILDDIR="${S}"
	install -d ${D}${libdir}/pkgconfig
	install -d ${D}${libdir}/python${PYTHON_MAJMIN}/config
	
	oe_runmake HOSTPGEN=${STAGING_BINDIR_NATIVE}/python-native3/pgen \
		HOSTPYTHON=${STAGING_BINDIR_NATIVE}/python-native3/python3 \
		CROSSPYTHONPATH=${STAGING_LIBDIR_NATIVE}/python${PYTHON_MAJMIN}/lib-dynload/ \
		STAGING_LIBDIR=${STAGING_LIBDIR} \
		STAGING_INCDIR=${STAGING_INCDIR} \
		STAGING_BASELIBDIR=${STAGING_BASELIBDIR} \
		BUILD_SYS=${BUILD_SYS} HOST_SYS=${HOST_SYS} \
		LIB=${baselib} \
		ARCH=${TARGET_ARCH} \
		DESTDIR=${D} LIBDIR=${libdir} install

	install -m 0644 Makefile.sysroot ${D}/${libdir}/python${PYTHON_MAJMIN}/config/Makefile

	if [ -e ${WORKDIR}/sitecustomize.py ]; then
		install -m 0644 ${WORKDIR}/sitecustomize.py ${D}/${libdir}/python${PYTHON_MAJMIN}
	fi

	oe_multilib_header python${PYTHON_MAJMIN}/pyconfig.h
}

SSTATE_SCAN_FILES += "Makefile"
PACKAGE_PREPROCESS_FUNCS += "py_package_preprocess"

py_package_preprocess () {
	# copy back the old Makefile to fix target package
	install -m 0644 Makefile.orig ${PKGD}/${libdir}/python${PYTHON_MAJMIN}/config/Makefile
}

require python-${PYTHON_MAJMIN}-manifest.inc

# manual dependency additions
RPROVIDES_${PN}-core = "${PN}"
RRECOMMENDS_${PN}-core = "${PN}-readline"
RRECOMMENDS_${PN}-crypt = "openssl"
RRECOMMENDS_${PN}-crypt_class-nativesdk = "nativesdk-openssl"

FILES_${PN}-2to3 += "${bindir}/2to3-${PYTHON_MAJMIN}"
FILES_${PN}-pydoc += "${bindir}/pydoc${PYTHON_MAJMIN} ${bindir}/pydoc3"
FILES_${PN}-idle += "${bindir}/idle3 ${bindir}/idle${PYTHON_MAJMIN}"

PACKAGES =+ "${PN}-pyvenv"
FILES_${PN}-pyvenv += "${bindir}/pyvenv-${PYTHON_MAJMIN} ${bindir}/pyvenv"

# package libpython3
PACKAGES =+ "libpython3 libpython3-staticdev"
FILES_libpython3 = "${libdir}/libpython*.so.*"
FILES_libpython3-staticdev += "${libdir}/python${PYTHON_MAJMIN}/config-${PYTHON_BINABI}/libpython${PYTHON_BINABI}.a"

# catch debug extensions (isn't that already in python-core-dbg?)
FILES_${PN}-dbg += "${libdir}/python${PYTHON_MAJMIN}/lib-dynload/.debug"

# catch all the rest (unsorted)
PACKAGES += "${PN}-misc"
FILES_${PN}-misc = "${libdir}/python${PYTHON_MAJMIN}"

# catch manpage
PACKAGES += "${PN}-man"
FILES_${PN}-man = "${datadir}/man"

BBCLASSEXTEND = "nativesdk"