// DcomConfigurator.cpp : Defines the entry point for the console application.
//

#include "stdafx.h"


int _tmain(int argc, _TCHAR* argv[])
{
	DcomConfigurator con(argc, argv);

	if(con.isHelp()) {
		con.usage();
		return 0;
	}
	con.configure();

	return 0;
}

DcomConfigurator::DcomConfigurator(int argc, _TCHAR* argv[]) {
	verbose = false;
	help = false;
	force = false;
	notReally = false;
	admin = new Persona(WinBuiltinAdministratorsSid);
	adminOwnerId = admin->getSidString();
	scriptingOwnerId = getOwnerString(HKEY_CLASSES_ROOT, REG_SCRIPTING);
	wmiOwnerId = getOwnerString(HKEY_CLASSES_ROOT, REG_WMI);
	parse(argc, argv);
}

DcomConfigurator::~DcomConfigurator() {
	delete admin;
}

void DcomConfigurator::usage()
{
	printf("Usage: DCOMConfigurator [-h|--help] [-v|--verbose] [-f|--force] [-n|--dry-run]\n");
	printf("\n");
	printf("DCOM Configurator attempts to change permissions and possibly the ownership of 2 Registry keys.\nThe keys allow access to WMI and Scripting.\n");
	printf("\n");
	printf("[-h|--help]    Show this help\n");
	printf("[-v|--verbose] Explain what happened\n");
	printf("[-f|--force]   XXXXXXXX\n");
	printf("[-n|--dry-run] Don't really do it\n");
	
	
	bool b = is64();
	if(b) {
		printf("\nYou are running on 64 bit Windows.  The 2 keys are automatically mapped to 32-bit registry keys.\n");
		printf("This is of no concern unless you use the Registry Editor (regedit.exe).  In that case you need to look in (1) instead of (2)\n");
		printf("(1) HKEY_CLASSES_ROOT\\Wow6432Node\\CLSID\\...\n");
		printf("(2) HKEY_CLASSES_ROOT\\CLSID\\...\n");
	}
	else
		printf("\nYou are running on 32 bit Windows.");
}

/**
  * Ugly and fast to develop.  Feel free to improve it...
  * Probably should re-work if an item #5 or 6 is added.  
  */

void DcomConfigurator::parse(int argc, _TCHAR* argv[])
{
	for(int i = 1; i < argc; i++) {
		// only do this code below 0 or 1 time
		if(verbose == false) {
			if(!lstrcmpi(L"-v", argv[i]) || !lstrcmpi(L"--verbose", argv[i])) {
				verbose = true;
				wprintf(L">>>>>>>>>>>>>                          <<<<<<<<<<<<<\n");
				wprintf(L">>>>>>>>>>>>>   Set to Verbose Mode    <<<<<<<<<<<<<\n");
				wprintf(L">>>>>>>>>>>>>                          <<<<<<<<<<<<<\n");
			}
		}
		if(!lstrcmpi(L"-h", argv[i]) || !lstrcmpi(L"--help", argv[i])) {
			help = true;
		}
		if(!lstrcmpi(L"-f", argv[i]) || !lstrcmpi(L"--force", argv[i])) {
			force = true;
		}
		if(!lstrcmpi(L"-n", argv[i]) || !lstrcmpi(L"--dry-run", argv[i])) {
			notReally = true;
		}
	}
}

void DcomConfigurator::configure() {
	Persona admin(WinBuiltinAdministratorsSid);

	if(verbose)
		printOwners();

	configureRegKeys();
	cout <<  message;
}

void DcomConfigurator::printOwners() {
	wprintf(L"Administrators group SID: [%s]\n", adminOwnerId);
	wprintf(L"Key: [%s]  Owner: [%s]\n", REG_SCRIPTING, scriptingOwnerId);
	wprintf(L"Key: [%s]  Owner: [%s]\n", REG_WMI, wmiOwnerId);

	if(is64()) {
	wprintf(L"Redirected Key: [%s]\n", REG_SCRIPTING_WOW, scriptingOwnerId);
	wprintf(L"Redirected Key: [%s]\n", REG_WMI_WOW, wmiOwnerId);
	}
}

void DcomConfigurator::configureRegKeys() {
	if(!Equal(adminOwnerId, scriptingOwnerId) || force) {
		TakeOwnership((LPTSTR)REG_SCRIPTING_FULL);
		message += "Took ownership and adjusted permissions of Scripting Registry Key.\n";
	}
	else
		message += "No need to adjust the Scripting Registry Key.\n";

	if(!Equal(adminOwnerId, wmiOwnerId) || force) {
		TakeOwnership((LPTSTR)REG_WMI_FULL);
		message += "Took ownership and adjusted permissions of WMI Registry Key.\n";
	}
	else
		message += "No need to adjust the WMI Registry Key.\n";
}

