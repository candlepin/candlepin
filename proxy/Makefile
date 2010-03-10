NAME 		:= candlepin
SPECFILE	= $(NAME).spec
VERSION         := $(shell rpm -q --qf "%{VERSION}\n" --specfile $(SPECFILE)| head -1)
# This needs to be set as an ENV variable (ie, Hudson sets it)
#RELEASE         := $(shell rpm -q --qf "%{RELEASE}\n" --specfile $(SPECFILE)| head -1)
UPSTREAM_NAME   = $(PROJECT)

TAG             = $(subst .,_,$(NAME)-$(VERSION)-$(RELEASE))

CVS             = cvs
RPMBUILD	= rpmbuild
INSTALL         = /usr/bin/install
INSTALL_DIR     = $(INSTALL) --verbose -d -m 755
ARCHIVE         = $(NAME)-bin.tar.gz
EXPLODED_NAME   = $(NAME)-$(VERSION).war

RPM_TOPDIR	= /tmp/$(NAME)-$(VERSION)-$(RELEASE)-build
_RPM_OPTS	= --define "_topdir	    $(RPM_TOPDIR)" \
		  --define "_release	    $(RELEASE)" \
		  --define "_builddir	    %{_topdir}" \
		  --define "_sourcedir	    $(shell pwd)" \
		  --define "_specdir	    $(shell pwd)" \
		  --define "_rpmdir	    $(shell pwd)" \
		  --define "_srcrpmdir 	    $(shell pwd)" \
		  --define '_rpmfilename    %%{NAME}-%%{VERSION}-%%{RELEASE}.%%{ARCH}.rpm'
RPM_OPTS        = $(strip $(_RPM_OPTS))

rpm: clean $(RPM_TOPDIR) $(SPECFILE) $(ARCHIVE)
	echo $(RELEASE)
	$(RPMBUILD) --clean $(RPM_OPTS) -bb $(SPECFILE)

clean:
	@rm -rfv *~ *.rpm $(RPM_TOPDIR) $(ARCHIVE); rm -rf target/$(NAME)

$(ARCHIVE):
	buildr clean package test=no; cd target; mkdir $(NAME); mv ./*.war $(NAME); cd $(NAME); jar -xvf ./*.war; rm ./*.war; cd ..; mv $(NAME) $(EXPLODED_NAME); tar cvzf ../$(ARCHIVE) $(EXPLODED_NAME); cd ..

$(RPM_TOPDIR):
	@$(INSTALL_DIR) $@
